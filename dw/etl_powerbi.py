#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Proyecto: Mi Nómina (VII Semestre - Ingeniería de Sistemas)
Asignatura: Visualización de Datos II (VD II)
Descripción: Proceso ETL que construye las tablas del Esquema en Estrella
             (Hecho_Turno, Dim_Fecha, Dim_Usuario, Dim_TipoDia, Dim_TipoHora, Dim_Quincena)
             anonimizando datos personales (Ley 1581 de 2012) y exportando archivos CSV
             listos para importar en Power BI Desktop.
"""

import csv
import datetime as dt
import hashlib
from pathlib import Path

FESTIVOS_CO_2026 = {
    dt.date(2026, 1, 1), dt.date(2026, 1, 12), dt.date(2026, 3, 23),
    dt.date(2026, 4, 2), dt.date(2026, 4, 3), dt.date(2026, 5, 1),
    dt.date(2026, 5, 18), dt.date(2026, 6, 8), dt.date(2026, 6, 15),
    dt.date(2026, 6, 29), dt.date(2026, 7, 20), dt.date(2026, 8, 7),
    dt.date(2026, 8, 17), dt.date(2026, 10, 12), dt.date(2026, 11, 2),
    dt.date(2026, 11, 16), dt.date(2026, 12, 8), dt.date(2026, 12, 25),
}


def anonimizar_usuario(user_id: str) -> str:
    digest = hashlib.sha256(user_id.encode("utf-8")).hexdigest()[:8].upper()
    return f"USR-{digest}"


def generar_dataset_estrella(directorio_salida: Path) -> None:
    directorio_salida.mkdir(parents=True, exist_ok=True)

    # 1. Dim_Usuario
    id_usr = anonimizar_usuario("usuario-operativo-cartagena-01")
    dim_usuario = [{"id_usuario_dw": id_usr, "codigo_anonimo": id_usr, "rol": "USER"}]

    # 2. Dim_TipoDia
    dim_tipo_dia = [
        {"id_tipo_dia": 1, "codigo": "NORMAL", "descripcion": "Turno laborado", "remunerado": True},
        {"id_tipo_dia": 2, "codigo": "INCAPACIDAD", "descripcion": "Incapacidad médica", "remunerado": True},
        {"id_tipo_dia": 3, "codigo": "COMPENSATORIO", "descripcion": "Día compensatorio", "remunerado": True},
        {"id_tipo_dia": 4, "codigo": "NO_LABORADO", "descripcion": "No citado / Descanso", "remunerado": False},
    ]

    # 3. Dim_TipoHora
    dim_tipo_hora = [
        {"id_tipo_hora": 1, "codigo": "ordD", "nombre": "Ordinaria Diurna", "categoria": "Ordinaria", "porcentaje_recargo_2026": 0.0},
        {"id_tipo_hora": 2, "codigo": "ordN", "nombre": "Ordinaria Nocturna", "categoria": "Nocturna", "porcentaje_recargo_2026": 35.0},
        {"id_tipo_hora": 3, "codigo": "extD", "nombre": "Extra Diurna", "categoria": "Extra", "porcentaje_recargo_2026": 25.0},
        {"id_tipo_hora": 4, "codigo": "extN", "nombre": "Extra Nocturna", "categoria": "Extra / Nocturna", "porcentaje_recargo_2026": 75.0},
        {"id_tipo_hora": 5, "codigo": "domD", "nombre": "Dominical o Festiva Diurna", "categoria": "Dominical/Festiva", "porcentaje_recargo_2026": 90.0},
        {"id_tipo_hora": 6, "codigo": "domN", "nombre": "Dominical o Festiva Nocturna", "categoria": "Dominical/Festiva + Noc.", "porcentaje_recargo_2026": 125.0},
        {"id_tipo_hora": 7, "codigo": "edd", "nombre": "Extra Dominical/Festiva Diurna", "categoria": "Extra Dominical", "porcentaje_recargo_2026": 115.0},
        {"id_tipo_hora": 8, "codigo": "edn", "nombre": "Extra Dominical/Festiva Nocturna", "categoria": "Extra Dominical Nocturna", "porcentaje_recargo_2026": 165.0},
    ]

    # 4. Dim_Quincena y simulación de 3 quincenas (2 cerradas + 1 abierta)
    quincenas_cfg = [
        ("Q-2026-10-A", dt.date(2026, 10, 1), dt.date(2026, 10, 15), "CERRADA"),
        ("Q-2026-10-B", dt.date(2026, 10, 16), dt.date(2026, 10, 30), "CERRADA"),
        ("Q-2026-11-A", dt.date(2026, 11, 1), dt.date(2026, 11, 15), "ABIERTA"),
    ]

    valor_hora = 8337.0
    aux_diario = 249095.0 / 30.0
    dim_fecha = {}
    dim_quincena = []
    hecho_turno = []
    seq = 1

    nombres_dias = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"]
    nombres_meses = {10: "Octubre", 11: "Noviembre"}

    for id_q, f_ini, f_fin, estado in quincenas_cfg:
        neto_q = 0.0
        dias = (f_fin - f_ini).days + 1
        for d in range(dias):
            f = f_ini + dt.timedelta(days=d)
            id_f = f.year * 10000 + f.month * 100 + f.day
            es_dom = f.weekday() == 6
            es_fest = f in FESTIVOS_CO_2026

            dim_fecha[id_f] = {
                "id_fecha": id_f,
                "fecha": f.isoformat(),
                "dia": f.day,
                "semana_anio": f.isocalendar()[1],
                "quincena_mes": "Q1 (1-15)" if f.day <= 15 else "Q2 (16-Fin)",
                "mes": f.month,
                "nombre_mes": nombres_meses.get(f.month, str(f.month)),
                "anio": f.year,
                "dia_semana": nombres_dias[f.weekday()],
                "es_domingo": es_dom,
                "es_festivo": es_fest,
            }

            # Descanso cada 7 días
            if d in (6, 13):
                continue

            ord_d = ord_n = ext_d = ext_n = dom_d = dom_n = 0.0
            if es_dom or es_fest:
                dom_n = 8.0
                bruto = dom_n * valor_hora * 2.25
                id_hora_pred = 6
            elif d % 2 == 0:
                ord_n = 7.5
                ord_d = 0.5
                bruto = ord_d * valor_hora + ord_n * valor_hora * 1.35
                id_hora_pred = 2
            else:
                ord_d = 8.0
                ext_d = 1.0 if d % 3 == 0 else 0.0
                bruto = ord_d * valor_hora + ext_d * valor_hora * 1.25
                id_hora_pred = 1

            deducciones = bruto * 0.08
            neto = bruto + aux_diario - deducciones
            neto_q += neto

            hecho_turno.append({
                "id_hecho": f"HT-{seq:03d}",
                "id_fecha": id_f,
                "id_usuario_dw": id_usr,
                "id_tipo_dia": 1,
                "id_quincena": id_q,
                "id_tipo_hora_predominante": id_hora_pred,
                "horas_ordinarias_diurnas": round(ord_d, 2),
                "horas_ordinarias_nocturnas": round(ord_n, 2),
                "horas_extras_diurnas": round(ext_d, 2),
                "horas_extras_nocturnas": round(ext_n, 2),
                "horas_dominicales_diurnas": round(dom_d, 2),
                "horas_dominicales_nocturnas": round(dom_n, 2),
                "horas_extras_dom_diurnas": 0.0,
                "horas_extras_dom_nocturnas": 0.0,
                "horas_totales": round(ord_d + ord_n + ext_d + ext_n + dom_d + dom_n, 2),
                "pago_salarial_bruto": round(bruto, 2),
                "auxilio_transporte": round(aux_diario, 2),
                "deducciones_salud_pension": round(deducciones, 2),
                "pago_neto_turno": round(neto, 2),
            })
            seq += 1

        dim_quincena.append({
            "id_quincena": id_q,
            "periodo": f"{f_ini.isoformat()} a {f_fin.isoformat()}",
            "desde": f_ini.isoformat(),
            "hasta": f_fin.isoformat(),
            "estado": estado,
            "neto_total": round(neto_q, 2),
        })

    tablas = {
        "Dim_Usuario.csv": dim_usuario,
        "Dim_TipoDia.csv": dim_tipo_dia,
        "Dim_TipoHora.csv": dim_tipo_hora,
        "Dim_Fecha.csv": list(dim_fecha.values()),
        "Dim_Quincena.csv": dim_quincena,
        "Hecho_Turno.csv": hecho_turno,
    }

    for nombre_archivo, filas in tablas.items():
        ruta = directorio_salida / nombre_archivo
        if filas:
            with ruta.open("w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=list(filas[0].keys()))
                writer.writeheader()
                writer.writerows(filas)
        print(f"[ETL DW] Generado {ruta} ({len(filas)} registros)")


if __name__ == "__main__":
    salida = Path(__file__).resolve().parent / "csv_powerbi"
    generar_dataset_estrella(salida)
