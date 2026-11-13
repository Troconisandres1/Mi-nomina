-- ============================================================================
-- PROYECTO: Mi Nómina (VII Semestre - Ingeniería de Sistemas)
-- ASIGNATURA: Visualización de Datos II (VD II)
-- DESCRIPCIÓN: Esquema en Estrella (Kimball & Ross) para el Data Warehouse
--              de liquidación de turnos quincenales y conexión con Power BI.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS dw_minomina;
SET search_path TO dw_minomina;

-- 1. Dimensión Fecha (Dim_Fecha)
CREATE TABLE IF NOT EXISTS dim_fecha (
    id_fecha           INTEGER PRIMARY KEY,          -- Formato YYYYMMDD (ej. 20261101)
    fecha              DATE NOT NULL UNIQUE,
    dia                SMALLINT NOT NULL,
    semana_anio        SMALLINT NOT NULL,
    quincena_mes       VARCHAR(20) NOT NULL,         -- 'Q1 (1-15)' o 'Q2 (16-Fin)'
    mes                SMALLINT NOT NULL,
    nombre_mes         VARCHAR(20) NOT NULL,
    anio               SMALLINT NOT NULL,
    dia_semana         VARCHAR(20) NOT NULL,
    es_domingo         BOOLEAN NOT NULL DEFAULT FALSE,
    es_festivo         BOOLEAN NOT NULL DEFAULT FALSE
);

-- 2. Dimensión Usuario Anonimizado (Dim_Usuario - Cumplimiento Ley 1581 de 2012)
CREATE TABLE IF NOT EXISTS dim_usuario (
    id_usuario_dw      VARCHAR(24) PRIMARY KEY,      -- Hash SHA-256 truncado (ej. USR-8F3A21)
    codigo_anonimo     VARCHAR(24) NOT NULL,
    rol                VARCHAR(16) NOT NULL          -- 'USER' o 'ADMIN'
);

-- 3. Dimensión Tipo de Día (Dim_TipoDia)
CREATE TABLE IF NOT EXISTS dim_tipo_dia (
    id_tipo_dia        SMALLINT PRIMARY KEY,
    codigo             VARCHAR(24) NOT NULL UNIQUE,  -- NORMAL, INCAPACIDAD, COMPENSATORIO, NO_LABORADO
    descripcion        VARCHAR(60) NOT NULL,
    remunerado         BOOLEAN NOT NULL
);

INSERT INTO dim_tipo_dia (id_tipo_dia, codigo, descripcion, remunerado) VALUES
    (1, 'NORMAL',        'Turno laborado',        TRUE),
    (2, 'INCAPACIDAD',   'Incapacidad médica',    TRUE),
    (3, 'COMPENSATORIO', 'Día compensatorio',     TRUE),
    (4, 'NO_LABORADO',   'No citado / Descanso',  FALSE)
ON CONFLICT (id_tipo_dia) DO NOTHING;

-- 4. Dimensión Tipo de Hora (Dim_TipoHora)
CREATE TABLE IF NOT EXISTS dim_tipo_hora (
    id_tipo_hora             SMALLINT PRIMARY KEY,
    codigo                   VARCHAR(12) NOT NULL UNIQUE,
    nombre                   VARCHAR(60) NOT NULL,
    categoria                VARCHAR(40) NOT NULL,
    porcentaje_recargo_2026  NUMERIC(6,2) NOT NULL
);

INSERT INTO dim_tipo_hora (id_tipo_hora, codigo, nombre, categoria, porcentaje_recargo_2026) VALUES
    (1, 'ordD', 'Ordinaria Diurna',                  'Ordinaria',                   0.00),
    (2, 'ordN', 'Ordinaria Nocturna',                'Nocturna',                   35.00),
    (3, 'extD', 'Extra Diurna',                      'Extra',                      25.00),
    (4, 'extN', 'Extra Nocturna',                    'Extra / Nocturna',           75.00),
    (5, 'domD', 'Dominical o Festiva Diurna',        'Dominical/Festiva',          90.00),
    (6, 'domN', 'Dominical o Festiva Nocturna',      'Dominical/Festiva + Noc.',  125.00),
    (7, 'edd',  'Extra Dominical/Festiva Diurna',    'Extra Dominical',           115.00),
    (8, 'edn',  'Extra Dominical/Festiva Nocturna',  'Extra Dominical Nocturna',  165.00)
ON CONFLICT (id_tipo_hora) DO NOTHING;

-- 5. Dimensión Quincena (Dim_Quincena)
CREATE TABLE IF NOT EXISTS dim_quincena (
    id_quincena        VARCHAR(32) PRIMARY KEY,
    periodo            VARCHAR(50) NOT NULL,
    desde              DATE NOT NULL,
    hasta              DATE NOT NULL,
    estado             VARCHAR(16) NOT NULL,         -- 'ABIERTA' o 'CERRADA'
    neto_total         NUMERIC(14,2) NOT NULL DEFAULT 0
);

-- 6. Tabla de Hechos Central (Hecho_Turno)
-- Grano: Un turno liquidado por día y por usuario.
CREATE TABLE IF NOT EXISTS hecho_turno (
    id_hecho                     VARCHAR(36) PRIMARY KEY,
    id_fecha                     INTEGER NOT NULL REFERENCES dim_fecha(id_fecha),
    id_usuario_dw                VARCHAR(24) NOT NULL REFERENCES dim_usuario(id_usuario_dw),
    id_tipo_dia                  SMALLINT NOT NULL REFERENCES dim_tipo_dia(id_tipo_dia),
    id_quincena                  VARCHAR(32) NOT NULL REFERENCES dim_quincena(id_quincena),
    id_tipo_hora_predominante    SMALLINT NOT NULL REFERENCES dim_tipo_hora(id_tipo_hora),
    horas_ordinarias_diurnas     NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_ordinarias_nocturnas   NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_extras_diurnas         NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_extras_nocturnas       NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_dominicales_diurnas    NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_dominicales_nocturnas  NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_extras_dom_diurnas     NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_extras_dom_nocturnas   NUMERIC(6,2) NOT NULL DEFAULT 0,
    horas_totales                NUMERIC(6,2) NOT NULL DEFAULT 0,
    pago_salarial_bruto          NUMERIC(14,2) NOT NULL DEFAULT 0,
    auxilio_transporte           NUMERIC(14,2) NOT NULL DEFAULT 0,
    deducciones_salud_pension    NUMERIC(14,2) NOT NULL DEFAULT 0,
    pago_neto_turno              NUMERIC(14,2) NOT NULL DEFAULT 0
);

-- Vista analítica de KPIs para conexión directa en Power BI
CREATE OR REPLACE VIEW vw_kpis_quincena AS
SELECT
    q.id_quincena,
    q.periodo,
    q.estado,
    COUNT(h.id_hecho) AS dias_registrados,
    ROUND((COUNT(h.id_hecho)::NUMERIC / 15.0) * 100, 2) AS cumplimiento_meta_15d_pct,
    SUM(h.horas_totales) AS total_horas,
    ROUND(
        CASE WHEN SUM(h.horas_totales) > 0 THEN
            ((SUM(h.horas_ordinarias_nocturnas + h.horas_extras_nocturnas + h.horas_dominicales_nocturnas + h.horas_extras_dom_nocturnas)) / SUM(h.horas_totales)) * 100
        ELSE 0 END, 2
    ) AS pct_horas_nocturnas,
    ROUND(
        CASE WHEN SUM(h.horas_totales) > 0 THEN
            ((SUM(h.horas_dominicales_diurnas + h.horas_dominicales_nocturnas + h.horas_extras_dom_diurnas + h.horas_extras_dom_nocturnas)) / SUM(h.horas_totales)) * 100
        ELSE 0 END, 2
    ) AS pct_horas_festivas,
    SUM(h.pago_neto_turno) AS neto_acumulado,
    ROUND(AVG(h.pago_neto_turno), 2) AS ingreso_promedio_dia,
    ROUND(AVG(h.pago_neto_turno) * 15, 2) AS neto_proyectado_15d
FROM dim_quincena q
LEFT JOIN hecho_turno h ON h.id_quincena = q.id_quincena
GROUP BY q.id_quincena, q.periodo, q.estado;
