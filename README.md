# Mi Nómina — Backend Spring Boot

Backend que reemplaza **Firebase Auth + Firestore + el servidor Node de IA** por un único
servicio Spring Boot con JWT propio, base de datos relacional y proxy a Gemini.

## 1. Requisitos

- Java 17+
- Maven 3.9+
- (Opcional en dev) Nada más: el perfil `dev` usa **H2 embebida**, no necesitas instalar nada.
- (Producción) PostgreSQL 14+

## 2. Arrancar en local (perfil `dev`, con H2)

> Nota: este proyecto no incluye el Maven Wrapper (`mvnw`) porque se generó sin
> acceso a internet. Usa tu Maven local (`mvn -version` para comprobar que lo
> tienes) o genera el wrapper tú mismo con `mvn -N io.takari:maven:wrapper`.

```bash
cd mi-nomina-backend
mvn spring-boot:run
```

Por defecto usa el perfil `dev` (definido en `application.yml`), que crea un archivo
`./data/mi-nomina-db.mv.db` — no necesitas Postgres para probar todo el flujo.

**Abre `http://localhost:8080/` directamente en el navegador** (no abras
`index.html` con doble clic). El propio backend sirve el HTML desde
`src/main/resources/static/index.html`, así que todo queda en el mismo origen
y no hay problemas de CORS. Si abres el HTML suelto con doble clic
(`file:///...`), el navegador lo trata como un origen especial (`null`) que
los backends no pueden autorizar de forma estándar — por eso este proyecto ya
viene con el HTML integrado, para evitarte ese problema.

### Variables de entorno importantes

| Variable | Para qué sirve | Ejemplo |
|---|---|---|
| `JWT_SECRET` | Firma de los tokens JWT (mín. 32 caracteres) | `openssl rand -base64 48` |
| `GEMINI_API_KEY` | Clave de la API de Gemini para el asistente de IA | `AIza...` |
| `ADMIN_EMAIL` | Correo que al registrarse queda ADMIN y aprobado automáticamente | `tu@correo.com` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (donde sirvas el HTML) | `http://localhost:5500` |
| `SPRING_PROFILES_ACTIVE` | `dev` (H2) o `prod` (Postgres) | `prod` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Solo en `prod` | — |

**Importante:** en producción SIEMPRE define `JWT_SECRET` y `GEMINI_API_KEY` tú mismo;
los valores por defecto en `application.yml` son solo para que arranque en dev.

## 3. Arrancar en producción con PostgreSQL

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/minomina
export DB_USER=minomina
export DB_PASSWORD=tu_password
export JWT_SECRET=$(openssl rand -base64 48)
export GEMINI_API_KEY=tu_api_key_de_gemini
export ADMIN_EMAIL=tu@correo.com
export CORS_ALLOWED_ORIGINS=https://tu-dominio.com

mvn spring-boot:run
```

En `prod`, `ddl-auto` está en `validate` (no crea tablas automáticamente). Para la
primera vez, arranca una vez con `SPRING_PROFILES_ACTIVE=prod` pero con
`spring.jpa.hibernate.ddl-auto=update` temporalmente (o añade Flyway/Liquibase si
vas a mantener esto a largo plazo — este proyecto no lo incluye por simplicidad).

## 4. Endpoints principales

Todos los endpoints (salvo `/api/auth/**`) requieren el header:
`Authorization: Bearer <token>`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/registro` | Crear cuenta (queda `PENDIENTE` salvo que sea el `ADMIN_EMAIL`) |
| POST | `/api/auth/login` | Login → devuelve `{ token, userId, email, nombre, estado, rol }` |
| GET | `/api/admin/usuarios/pendientes` | (solo ADMIN) listar cuentas por aprobar |
| POST | `/api/admin/usuarios/{id}/aprobar` | (solo ADMIN) aprobar cuenta |
| POST | `/api/admin/usuarios/{id}/rechazar` | (solo ADMIN) rechazar cuenta |
| GET | `/api/registros` | Listar turnos de la quincena actual |
| POST | `/api/registros` | Guardar/actualizar un día (`NORMAL`, `INCAPACIDAD`, `COMPENSATORIO`, `NO_LABORADO`) |
| DELETE | `/api/registros/{fecha}` | Eliminar un día (`YYYY-MM-DD`) |
| GET | `/api/registros/resumen` | IBC, transporte, deducciones, neto |
| POST | `/api/registros/recalcular` | Recalcula todos los turnos NORMAL con el motor actual |
| GET | `/api/config` | Configuración salarial del usuario |
| PUT | `/api/config` | Actualizar configuración salarial |
| GET | `/api/historial` | Listar quincenas cerradas |
| POST | `/api/historial/cerrar-quincena` | Cierra la quincena actual y la mueve al historial |
| DELETE | `/api/historial/{id}` | Eliminar una quincena del historial |
| POST | `/api/ai/chat` | `{ mensaje, historialReciente: [] }` → `{ respuesta }` |

## 5. Qué se portó y qué quedó simplificado

**Portado 1:1 desde el JS original:**
- Motor de cálculo de nómina (`PayrollEngine`): descanso no remunerado, límites
  diarios (10h / 8h dom-fest), contador semanal de 44h→42h, clasificación
  minuto a minuto de horas ordinarias/extra/nocturnas/dominicales, y los dos
  cortes legales de julio 2026 (dominical 90%, jornada 42h, valor hora $8.337).
- Reglas de incapacidad (100% del salario diario, hasta 4 días) y compensatorio
  (valor fijo).
- Cálculo de IBC, auxilio de transporte, deducciones 8% (salud+pensión) y neto.

**Simplificado a propósito (para no sobre-extender el alcance):**
- **Festivos**: lista fija de 2026 en `FestivosService` (antes se consultaba una
  API pública en el frontend). Si necesitas años futuros, amplía esa lista o
  agrega una llamada `@Scheduled` a `date.nager.at` con caché.
- **PDF / Excel**: la generación con `jsPDF` / `SheetJS` puede seguir siendo
  100% client-side (ya funciona con los datos que te devuelven `/api/registros`
  y `/api/historial`); no hacía falta moverla al backend.
- **Temas de color / modo oscuro**: es UI pura, no toca el backend, no se tocó.

## 6. Adaptar el frontend (el HTML que ya tienes)

Tu HTML actual llama directamente a Firebase (`signInWithEmailAndPassword`,
`setDoc`, etc.) y a un servidor Node en Render para la IA. Hay que reemplazar
**solo** esas partes por `fetch` a esta API. Los `id`s del DOM, el CSS y toda la
lógica de UI (pestañas, gráficos, exportar PDF/Excel, temas) **no cambian**.

Puntos exactos a reemplazar en tu archivo:

1. **Bloque `<script type="module">` de Firebase** → bórralo completo y usa
   `fetch` a `/api/auth/login` y `/api/auth/registro`. Guarda el `token` que
   te devuelven en `localStorage` (o en memoria) y mándalo como
   `Authorization: Bearer <token>` en cada llamada.
2. **`window.saveToCloud`** → en vez de `setDoc(doc(fb_db,...))`, hacer
   `POST /api/registros` (por cada registro nuevo/editado), `PUT /api/config`,
   `POST /api/historial/cerrar-quincena`, etc. según qué cambió.
3. **`syncFromCloud`** → `GET /api/registros` + `GET /api/config` +
   `GET /api/historial` al iniciar sesión, y llenar `window.db`,
   `window.cfg`, `window.historialDB` igual que antes.
4. **`window.AI_URL` / `sendChat()`** → cambia la URL a
   `TU_BACKEND/api/ai/chat` y el body a `{ mensaje, historialReciente }`;
   la respuesta llega en `data.respuesta` en vez de
   `data.candidates[0].content.parts[...]` (eso ya lo arma el backend).

Te dejo un archivo de ejemplo (`frontend-adapter-example.js`) con las 4 piezas
reescritas y comentadas para que las copies dentro de tu HTML en lugar del
bloque de Firebase. No reemplaza el archivo completo a propósito: es más
seguro que integres estas piezas tú mismo revisando que cada `id` del DOM siga
coincidiendo con tu versión actual del HTML.

## 7. Por qué "sin Firebase" pero antes había una contradicción

Pediste explícitamente "todo, sin Firebase", así que la autenticación quedó
100% en Spring Boot con JWT + bcrypt (no se usa Firebase Auth para nada). El
flujo de aprobación manual de cuentas (`PENDIENTE` → `APROBADO`) se mantiene
igual que tenías, solo que ahora lo apruebas con
`POST /api/admin/usuarios/{id}/aprobar` en vez de cambiar un campo en
Firestore Console.
