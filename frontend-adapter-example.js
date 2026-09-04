/*
 * ADAPTADOR FRONTEND — reemplaza el bloque <script type="module"> de Firebase
 * de tu HTML por algo equivalente a esto. Ajusta API_BASE a donde despliegues
 * el backend Spring Boot.
 *
 * No es un archivo que se importe tal cual: son las 4 piezas a integrar,
 * comentadas, para que las pegues donde estaban las funciones de Firebase.
 */

const API_BASE = 'http://localhost:8080/api'; // cambia por tu dominio en producción

window.currentUser = null;
window.authToken = null;

function authHeaders() {
  return window.authToken ? { 'Authorization': `Bearer ${window.authToken}` } : {};
}

// ── 1. LOGIN / REGISTRO (reemplaza handleAuth / handleSignup / onAuthStateChanged) ──

window.handleAuth = async () => {
  const email = document.getElementById('authEmail').value.trim();
  const pass = document.getElementById('authPassword').value;
  const err = document.getElementById('authError');
  err.style.display = 'none';
  if (!email || !pass) { err.innerText = 'Ingresa correo y contraseña'; err.style.display = 'block'; return; }

  try {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: pass })
    });
    const data = await res.json();

    if (!res.ok) {
      err.innerText = data.error || 'Correo o contraseña incorrectos.';
      err.style.display = 'block';
      return;
    }

    window.authToken = data.token;
    window.currentUser = { uid: data.userId, email: data.email };
    window.currentUserName = data.nombre;
    try { localStorage.setItem('nomina_token', data.token); } catch (e) {}

    document.getElementById('authContainer').classList.remove('visible');
    await syncFromCloud();
  } catch (e) {
    err.innerText = 'No se pudo conectar con el servidor.';
    err.style.display = 'block';
  }
};

window.handleSignup = async () => {
  const nombre = document.getElementById('signupNombre').value.trim();
  const email = document.getElementById('signupEmail').value.trim();
  const pass = document.getElementById('signupPassword').value;
  const confirm = document.getElementById('signupPasswordConfirm').value;
  const err = document.getElementById('signupError');
  err.style.display = 'none';

  if (!nombre || !email || !pass || !confirm) { err.innerText = 'Completa todos los campos'; err.style.display = 'block'; return; }
  if (pass !== confirm) { err.innerText = 'Las contraseñas no coinciden'; err.style.display = 'block'; return; }

  try {
    const res = await fetch(`${API_BASE}/auth/registro`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nombre, email, password: pass })
    });
    const data = await res.json();
    if (!res.ok) { err.innerText = data.error || 'Error al crear cuenta.'; err.style.display = 'block'; return; }
    window.switchAuthPanel('pending');
  } catch (e) {
    err.innerText = 'No se pudo conectar con el servidor.';
    err.style.display = 'block';
  }
};

window.handleSignOut = () => {
  window.syncReady = false;
  window.authToken = null;
  window.currentUser = null;
  try { localStorage.removeItem('nomina_token'); } catch (e) {}
  window.switchAuthPanel('login');
  document.getElementById('authContainer').classList.add('visible');
};

// Al cargar la página: si hay token guardado, intenta restaurar sesión.
(async function bootstrapSesion() {
  let token = null;
  try { token = localStorage.getItem('nomina_token'); } catch (e) {}
  if (!token) {
    window.switchAuthPanel('login');
    document.getElementById('authContainer').classList.add('visible');
    return;
  }
  window.authToken = token;
  // No hay endpoint "whoami" en este ejemplo minimal: si /api/registros
  // devuelve 401, se asume token vencido y se manda a login.
  const res = await fetch(`${API_BASE}/registros`, { headers: authHeaders() });
  if (res.status === 401 || res.status === 403) {
    window.handleSignOut();
    return;
  }
  document.getElementById('authContainer').classList.remove('visible');
  await syncFromCloud();
})();


// ── 2. CARGA INICIAL DE DATOS (reemplaza syncFromCloud) ──

async function syncFromCloud() {
  if (!window.authToken) return;
  try {
    const [registrosRes, cfgRes, histRes] = await Promise.all([
      fetch(`${API_BASE}/registros`, { headers: authHeaders() }),
      fetch(`${API_BASE}/config`, { headers: authHeaders() }),
      fetch(`${API_BASE}/historial`, { headers: authHeaders() }),
    ]);

    window.db = (await registrosRes.json()).map(adaptarRegistroDelBackend);
    window.cfg = await cfgRes.json();
    window.historialDB = (await histRes.json()).map(adaptarHistorialDelBackend);

    window.syncReady = true;
    document.getElementById('cfgHora').value = window.cfg.hora;
    document.getElementById('cfgTransp').value = window.cfg.auxTranspMensual;
    document.getElementById('cfgRN').value = window.cfg.rn;
    document.getElementById('cfgDOM').value = window.cfg.dom;
    document.getElementById('cfgHED').value = window.cfg.hed;
    document.getElementById('cfgHEN').value = window.cfg.hen;

    renderAll(); renderHistorial();
    if (!document.getElementById('dashboard').classList.contains('hidden')) renderDashboard();
  } catch (err) {
    showToast('No se pudieron cargar los datos. Verifica tu conexión y recarga la página.', 'error', 0);
  }
}

// El backend usa nombres tipo horaInicio/horaFin/descansoInicio/descansoFin;
// el frontend original espera r.rawHoras = {hI,hF,hBO,hBI}. Este adaptador
// traduce de uno a otro para no tocar el resto del HTML.
function adaptarRegistroDelBackend(r) {
  return {
    fecha: r.fecha,
    tipo: r.tipo,
    pagoSalarial: r.pagoSalarial,
    pagaTransporte: r.pagaTransporte,
    diag: r.diagnostico,
    desglose: r.desglose,
    rawHoras: r.horaInicio ? { hI: r.horaInicio, hF: r.horaFin, hBO: r.descansoInicio, hBI: r.descansoFin } : null,
  };
}

function adaptarHistorialDelBackend(h) {
  const registros = (h.snapshot && h.snapshot.registros) ? h.snapshot.registros : [];
  return { id: h.id, desde: h.desde, hasta: h.hasta, neto: h.neto, registros };
}


// ── 3. GUARDAR (reemplaza saveToCloud) ──
// En el original, saveToCloud() sube TODO (window.db + window.historialDB + window.cfg)
// de una sola vez porque Firestore es un documento. Con una API REST normal,
// cada acción llama a su propio endpoint. Ejemplos de reemplazo puntual:

// a) Guardar/editar un día (reemplaza el bloque dentro de formTurno.onsubmit,
//    justo donde antes hacías window.db.push(registro) + window.saveToCloud()):
async function guardarRegistroEnBackend(payload) {
  // payload = { fecha, tipo, horaInicio, horaFin, descansoInicio, descansoFin, diagnostico, numDiasIncapacidad }
  const res = await fetch(`${API_BASE}/registros`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(payload)
  });
  if (!res.ok) { const d = await res.json().catch(() => ({})); throw new Error(d.error || 'Error al guardar'); }
  window.db = (await res.json()).map(adaptarRegistroDelBackend);
  renderAll();
}

// b) Guardar configuración (reemplaza saveConfig -> window.saveToCloud):
window.saveConfig = async function () {
  window.cfg.hora = parseFloat(document.getElementById('cfgHora').value);
  window.cfg.auxTranspMensual = parseFloat(document.getElementById('cfgTransp').value);
  window.cfg.rn = parseFloat(document.getElementById('cfgRN').value);
  window.cfg.dom = parseFloat(document.getElementById('cfgDOM').value);
  window.cfg.hed = parseFloat(document.getElementById('cfgHED').value);
  window.cfg.hen = parseFloat(document.getElementById('cfgHEN').value);

  const res = await fetch(`${API_BASE}/config`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(window.cfg)
  });
  window.cfg = await res.json();
  showToast('Configuración guardada', 'success');
  renderAll();
};

// c) Cerrar quincena (reemplaza clearQuincena -> window.saveToCloud):
async function cerrarQuincenaEnBackend() {
  const res = await fetch(`${API_BASE}/historial/cerrar-quincena`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) { const d = await res.json().catch(() => ({})); throw new Error(d.error || 'No se pudo cerrar'); }
  window.db = [];
  const histRes = await fetch(`${API_BASE}/historial`, { headers: authHeaders() });
  window.historialDB = (await histRes.json()).map(adaptarHistorialDelBackend);
  renderAll(); renderHistorial();
}

// d) Eliminar un día (reemplaza deleteReg -> window.saveToCloud):
async function eliminarRegistroEnBackend(fecha) {
  await fetch(`${API_BASE}/registros/${fecha}`, { method: 'DELETE', headers: authHeaders() });
  window.db = window.db.filter(x => x.fecha !== fecha);
  renderAll();
}


// ── 4. ASISTENTE DE IA (reemplaza sendChat / window.AI_URL) ──

async function sendChat() {
  const input = document.getElementById('chatInput');
  const msg = input.value.trim();
  if (!msg) return;

  input.value = '';
  appendUserMsg(msg);
  input.disabled = true;
  document.getElementById('chatSendBtn').disabled = true;
  showThinking();

  try {
    const res = await fetch(`${API_BASE}/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ mensaje: msg, historialReciente: [] })
    });
    if (!res.ok) throw new Error('Error del servidor de IA');
    const data = await res.json();
    removeThinking();
    appendAIMsg(data.respuesta);
  } catch (err) {
    removeThinking();
    appendAIMsg('⚠️ Error al conectar con el asistente. Intenta de nuevo en unos segundos.');
  }
  input.disabled = false;
  document.getElementById('chatSendBtn').disabled = false;
  input.focus();
}
