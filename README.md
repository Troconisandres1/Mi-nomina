# Mi Nómina

**Mi Nómina** es una plataforma web de control, cálculo y análisis de nómina quincenal con agente inteligente, analítica de datos y optimización de turnos, desarrollada bajo la normativa laboral colombiana vigente (Código Sustantivo del Trabajo, Ley 2101 de 2021 y Ley 2466 de 2025).

Permite a los trabajadores por turnos registrar sus jornadas diarias, verificar automáticamente recargos ordinarios, nocturnos, dominicales, festivos y horas extras, consolidar el historial de quincenas cerradas, proyectar indicadores en un Data Warehouse con esquema en estrella para Power BI, optimizar la programación quincenal de turnos mediante Programación Lineal Entera (PLE) y realizar consultas sobre sus propios datos a través de un asistente conversacional basado en inteligencia artificial.

---

## Integrantes y Contexto Académico

- **Autores:** Andrés Camilo Troconis, Luis Castillo, Alan Cassiani y Marchena Urbina
- **Programa:** Ingeniería de Sistemas — VII Semestre (Cartagena de Indias, Colombia)
- **Asignaturas Integradas:**
  - **Desarrollo de Aplicaciones Web (DWA):** Doc. Fabio García
  - **Visualización de Datos II (VD II):** Doc. Lucía Mestre
  - **Investigación de Operaciones (IO):** Doc. Walter Espriella

---

## Características Principales

- **Motor de liquidación laboral (`PayrollEngine`):** Clasificación minuto a minuto de horas ordinarias diurnas, nocturnas (+35 %), dominicales y festivas (+80 % hasta el 30 de junio de 2026 y +90 % desde el 1 de julio de 2026) y horas extras, aplicando la reducción de la jornada máxima semanal (44 h a 42 h desde el 15 de julio de 2026), límites diarios (10 h en día hábil / 8 h en domingo o festivo), auxilio de transporte y deducciones de ley (8 % salud y pensión).
- **Gestión de novedades e historial:** Registro de turnos normales, incapacidades médicas, días compensatorios y días no citados, cierre de quincenas con instantánea histórica (`snapshot`) y exportación a PDF y Excel.
- **Agente inteligente con IA (`AiController` / `AiProxyService`):** Asistente conversacional conectado a Google Gemini (`gemini-2.5-flash`) con contexto aislado por usuario autenticado y rotación automática de claves API.
- **Data Warehouse y analítica en Power BI (`DataWarehouseEtlService`):** Proceso ETL que transforma y anonimiza los registros (`SHA-256`, Ley 1581 de 2012) hacia un modelo dimensional en estrella (`Hecho_Turno`, `Dim_Fecha`, `Dim_Usuario`, `Dim_TipoDia`, `Dim_TipoHora`, `Dim_Quincena`), con exportación directa a Excel/CSV/JSON e incrustación del dashboard de Power BI en la aplicación web.
- **Optimización matemática de turnos (`OptimizacionTurnosService`):** Modelo de Programación Lineal Entera Binaria (PLE) resuelto mediante Ramificación y Acotamiento (*Branch and Bound*) que determina qué turnos tomar en la quincena para maximizar el ingreso neto respetando topes semanales, límites diarios y descansos obligatorios, contrastando la ganancia frente a una asignación manual.
- **Seguridad y control de acceso:** Autenticación stateless mediante JWT (`jjwt`), contraseñas cifradas con `BCrypt` y flujo de aprobación de cuentas por administrador (`PENDIENTE` → `APROBADO`).

---

## Stack Tecnológico

- **Backend:** Java 17, Spring Boot 3.3.4 (Spring Web, Spring Security, Spring Data JPA, Bean Validation)
- **Seguridad:** JSON Web Tokens (`jjwt` 0.12.6) y `BCryptPasswordEncoder`
- **Base de Datos Transaccional y Analítica:** H2 Database (desarrollo) y PostgreSQL 14+ (producción y Data Warehouse)
- **Frontend:** HTML5, CSS3, JavaScript (ES6+), PWA, Chart.js, jsPDF y SheetJS
- **Inteligencia Artificial:** API REST de Google Gemini (`gemini-2.5-flash`)
- **Analítica e Investigación de Operaciones:** Power BI, SQL (Esquema en Estrella) y Python 3 (ETL y Solver PLE)
- **Construcción, Pruebas y CI:** Apache Maven, JUnit 5, JaCoCo y GitHub Actions

---

## Estructura del Proyecto

```text
Mi-nomina/
├── src/main/java/com/minomina/
│   ├── config/          # Configuración de Spring Security, CORS y filtros HTTP
│   ├── controller/      # Controladores REST (Auth, Registro, Historial, Config, Admin, IA, Analytics, Optimización)
│   ├── dto/             # Objetos de transferencia de datos (DTOs de entrada y salida)
│   ├── exception/       # Manejo centralizado de errores (GlobalExceptionHandler)
│   ├── model/           # Entidades JPA y enumeraciones (Usuario, Registro, Historial, ConfigNomina)
│   ├── repository/      # Repositorios Spring Data JPA
│   ├── security/        # Servicio JWT, filtro de autenticación y principal de usuario
│   ├── service/         # Lógica de negocio (PayrollEngine, AiProxyService, DataWarehouseEtlService, OptimizacionTurnosService)
│   └── util/            # Convertidores de persistencia JSON
├── src/main/resources/
│   ├── static/          # Interfaz web SPA/PWA (index.html)
│   └── application.yml  # Configuración de perfiles (dev con H2 y prod con PostgreSQL)
├── src/test/            # Pruebas unitarias de motor de nómina, autenticación, ETL y optimizador PLE
├── dw/                  # Scripts SQL del Data Warehouse (PostgreSQL) y ETL en Python para Power BI
├── io-solver/           # Implementación computacional en Python del modelo PLE (Branch & Bound)
├── docs/                # Documentación técnica, evidencias de VD II e IO, e Informe Metodológico Final
├── pom.xml              # Configuración de dependencias y plugins Maven
├── .env.example         # Plantilla de variables de entorno
└── run-local.ps1.example # Plantilla de ejecución local en PowerShell
```

---

## Endpoints Principales de la API REST

Todos los endpoints (excepto `/api/auth/**` y `/api/estado`) requieren el encabezado `Authorization: Bearer <token>`.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/registro` | Registro de usuario (estado inicial `PENDIENTE`) |
| `POST` | `/api/auth/login` | Inicio de sesión y emisión de token JWT |
| `GET` | `/api/admin/usuarios/pendientes` | Listado de cuentas pendientes de aprobación (`ADMIN`) |
| `POST` | `/api/admin/usuarios/{id}/aprobar` | Aprobación de cuenta de usuario (`ADMIN`) |
| `POST` | `/api/admin/usuarios/{id}/rechazar` | Rechazo de cuenta de usuario (`ADMIN`) |
| `GET` | `/api/registros` | Consulta de turnos registrados en la quincena activa |
| `POST` | `/api/registros` | Creación o actualización de un turno o novedad diaria |
| `DELETE` | `/api/registros/{fecha}` | Eliminación de un registro por fecha (`YYYY-MM-DD`) |
| `GET` | `/api/registros/resumen` | Liquidación consolidada (IBC, transporte, deducciones y neto) |
| `POST` | `/api/registros/recalcular` | Recálculo integral de los turnos de la quincena activa |
| `GET` / `PUT` | `/api/config` | Consulta y actualización de parámetros salariales del usuario |
| `GET` | `/api/historial` | Consulta de quincenas cerradas en el historial |
| `POST` | `/api/historial/cerrar-quincena` | Cierre de la quincena actual y almacenamiento de snapshot |
| `POST` | `/api/ai/chat` | Consulta al agente inteligente con contexto de nómina del usuario |
| `GET` | `/api/analytics/dw` | Extracción ETL del modelo dimensional en estrella y KPIs para Power BI |
| `GET` | `/api/optimizacion/catalogo` | Catálogo de turnos candidatos para el modelo matemático |
| `POST` | `/api/optimizacion/turnos` | Resolución del modelo PLE de asignación óptima de turnos |

---

## Requisitos e Instalación

### Requisitos Previos

- **Java JDK 17** o superior
- **Apache Maven 3.9+**
- **PostgreSQL 14+** *(requerido para el perfil `prod`; el perfil `dev` utiliza H2 embebida automáticamente)*

### Configuración de Variables de Entorno

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/Troconisandres1/Mi-nomina.git
   cd Mi-nomina
   ```
2. Crear el archivo local de variables a partir de la plantilla incluida (sin exponer credenciales en el control de versiones):
   ```powershell
   Copy-Item run-local.ps1.example run-local.ps1
   ```
3. Configurar en `run-local.ps1` (o mediante variables de entorno del sistema) los valores correspondientes:
   - `JWT_SECRET`: Clave secreta de al menos 32 caracteres para la firma de tokens JWT.
   - `GEMINI_API_KEYS`: Clave(s) de API de Google Gemini.
   - `ADMIN_EMAIL`: Correo electrónico del administrador principal.
   - `SPRING_PROFILES_ACTIVE`: `dev` (H2 local) o `prod` (PostgreSQL).
   - `DB_URL`, `DB_USER`, `DB_PASSWORD`: Credenciales de conexión a PostgreSQL para el perfil `prod`.

### Ejecución

- **En entorno de desarrollo (`dev`):**
  ```powershell
  .\run-local.ps1
  ```
  O directamente con Maven:
  ```bash
  mvn spring-boot:run
  ```
  La aplicación estará disponible en `http://localhost:8080/`.

- **Ejecución de pruebas unitarias y cobertura (JaCoCo):**
  ```bash
  mvn clean test
  ```
