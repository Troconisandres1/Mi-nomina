#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Proyecto: Mi Nómina (VII Semestre - Ingeniería de Sistemas)
Asignatura: Investigación de Operaciones (IO)
Docente: Walter Espriella
Autores: Andrés Camilo Troconis, Luis Castillo, Alan Cassiani

Descripción:
Implementación computacional del modelo de Programación Lineal Entera Binaria (PLE)
para maximizar el ingreso neto quincenal de un trabajador por turnos en Colombia
bajo las restricciones legales vigentes en 2026 (Ley 2101 de 2021: 42 h/semana;
Ley 2466 de 2025: recargo dominical/festivo del 90 % y nocturno del 35 %).

Formulación Matemática:
    max Z = sum_{d in D} sum_{s in S} v_{d,s} * x_{d,s}
    sujeto a:
        (R1) sum_{s in S} x_{d,s} <= 1                          para todo d in D
        (R2) sum_{d in w} sum_{s in S} h^{ord}_{d,s} x_{d,s} <= 42   para cada semana w
        (R3) h^{ord}_{d,s} <= 10 (hábil) / 8 (dom-fest); horas extras semanales <= 12
        (R4) x_{d-1,s1} + x_{d,s2} <= 1 si descanso(s1,s2) < 12h;
             máximo 6 días consecutivos sin descanso obligatorio
        x_{d,s} in {0, 1}
"""

import datetime as dt
from dataclasses import dataclass
from typing import List, Tuple

VALOR_HORA_2026 = 8337.0          # Vigente desde el 15 de julio de 2026 (jornada 42h)
AUX_TRANSPORTE_DIARIO = 249095.0 / 30.0
DEDUCCION_SALUD_PENSION = 0.08    # 4% salud + 4% pensión

FESTIVOS_NOV_2026 = {
    dt.date(2026, 11, 2),   # Día de Todos los Santos (trasladado)
    dt.date(2026, 11, 16),  # Independencia de Cartagena (trasladado)
}


@dataclass
class TurnoCandidato:
    codigo: str
    nombre: str
    inicio_min: int        # Minutos desde las 00:00 del día d
    fin_min: int           # Minutos desde las 00:00 (puede superar 1440 si cruza medianoche)
    horas_ord: float
    horas_ext: float
    horas_noc: float


CATALOGO_TURNOS: List[TurnoCandidato] = [
    TurnoCandidato("DIURNO_8H",    "Diurno Estándar (06:00-14:30)",     6 * 60,  14 * 60 + 30, 8.0, 0.0, 0.0),
    TurnoCandidato("MIXTO_8H",     "Mixto Tarde-Noche (14:00-22:30)",  14 * 60,  22 * 60 + 30, 8.0, 0.0, 3.5),
    TurnoCandidato("NOCTURNO_8H",  "Nocturno Completo (22:00-06:30)",  22 * 60,  30 * 60 + 30, 8.0, 0.0, 7.5),
    TurnoCandidato("EXTENDIDO_10H","Diurno Extendido (08:00-19:00)",    8 * 60,  19 * 60,      10.0, 0.0, 0.0),
    TurnoCandidato("NOCTURNO_10H", "Nocturno Extendido (20:00-06:30)", 20 * 60,  30 * 60 + 30, 10.0, 0.0, 9.5),
]


def es_dom_o_festivo(fecha: dt.date) -> bool:
    return fecha.weekday() == 6 or fecha in FESTIVOS_NOV_2026


def evaluar_turno_dia(turno: TurnoCandidato, fecha: dt.date) -> Tuple[float, float, float]:
    """
    Calcula (horas_ordinarias_efectivas, horas_extras_efectivas, ingreso_neto_v_ds)
    aplicando exactamente las reglas de PayrollEngine (corte 2026: dom/fest 90%, nocturno 35%).
    """
    fest = es_dom_o_festivo(fecha)
    limite_diario_ord = 8.0 if fest else 10.0
    horas_totales = turno.horas_ord + turno.horas_ext
    h_ord = min(horas_totales, limite_diario_ord)
    h_ext = max(0.0, horas_totales - limite_diario_ord)

    # Proporción de horas nocturnas en ordinarias y en extras
    prop_noc = turno.horas_noc / horas_totales if horas_totales > 0 else 0.0
    ord_noc = h_ord * prop_noc
    ord_diu = h_ord - ord_noc
    ext_noc = h_ext * prop_noc
    ext_diu = h_ext - ext_noc

    if not fest:
        bruto = (
            ord_diu * VALOR_HORA_2026 * 1.00
            + ord_noc * VALOR_HORA_2026 * 1.35
            + ext_diu * VALOR_HORA_2026 * 1.25
            + ext_noc * VALOR_HORA_2026 * 1.75
        )
    else:
        bruto = (
            ord_diu * VALOR_HORA_2026 * 1.90
            + ord_noc * VALOR_HORA_2026 * 2.25
            + ext_diu * VALOR_HORA_2026 * 2.15
            + ext_noc * VALOR_HORA_2026 * 2.65
        )

    deducciones = bruto * DEDUCCION_SALUD_PENSION
    neto = bruto + AUX_TRANSPORTE_DIARIO - deducciones
    return h_ord, h_ext, neto


def resolver_ple_branch_and_bound(
    fecha_inicio: dt.date = dt.date(2026, 11, 1),
    n_dias: int = 15,
    limite_semanal_ord: float = 42.0,
    max_consecutivos: int = 6,
    descanso_min_horas: int = 12,
    max_dias_quincena: int = 13,
):
    fechas = [fecha_inicio + dt.timedelta(days=i) for i in range(n_dias)]
    n_turnos = len(CATALOGO_TURNOS)

    # Matriz de coeficientes (h_ord, h_ext, v_ds)
    eval_matriz = [[evaluar_turno_dia(CATALOGO_TURNOS[s], fechas[d]) for s in range(n_turnos)] for d in range(n_dias)]

    # Matriz de compatibilidad de transición (R4: descanso mínimo entre salida de d-1 y entrada de d)
    compat = [[False] * n_turnos for _ in range(n_turnos)]
    for s1 in range(n_turnos):
        for s2 in range(n_turnos):
            descanso_min = (1440 + CATALOGO_TURNOS[s2].inicio_min) - CATALOGO_TURNOS[s1].fin_min
            compat[s1][s2] = descanso_min >= descanso_min_horas * 60

    max_por_dia = [max(eval_matriz[d][s][2] for s in range(n_turnos)) for d in range(n_dias)]
    cota_restante = [0.0] * (n_dias + 1)
    for d in range(n_dias - 1, -1, -1):
        cota_restante[d] = cota_restante[d + 1] + max_por_dia[d]

    mejor_z = -1.0
    mejor_plan = [-1] * n_dias
    asignacion = [-1] * n_dias
    nodos = 0

    def bnb(dia: int, consec: int, lab_total: int, ord_sem: float, ext_sem: float, z_acum: float):
        nonlocal mejor_z, mejor_plan, nodos
        nodos += 1

        if dia == n_dias:
            if z_acum > mejor_z:
                mejor_z = z_acum
                mejor_plan = asignacion.copy()
            return

        if z_acum + cota_restante[dia] <= mejor_z:
            return

        if dia % 7 == 0:
            ord_sem = 0.0
            ext_sem = 0.0

        # Ordenar turnos por mayor aporte neto v_ds
        orden = sorted(range(n_turnos), key=lambda s: eval_matriz[dia][s][2], reverse=True)

        if consec < max_consecutivos and lab_total < max_dias_quincena:
            prev = asignacion[dia - 1] if dia > 0 else -1
            for s in orden:
                if prev != -1 and not compat[prev][s]:
                    continue
                h_o, h_e, v_ds = eval_matriz[dia][s]
                if ord_sem + h_o > limite_semanal_ord + 1e-6:
                    continue
                if ext_sem + h_e > 12.0 + 1e-6:
                    continue
                asignacion[dia] = s
                bnb(dia + 1, consec + 1, lab_total + 1, ord_sem + h_o, ext_sem + h_e, z_acum + v_ds)
                asignacion[dia] = -1

        # Opción de descanso (x_{d,s} = 0 para todo s)
        asignacion[dia] = -1
        bnb(dia + 1, 0, lab_total, ord_sem, ext_sem, z_acum)

    bnb(0, 0, 0, 0.0, 0.0, 0.0)

    # Calcular asignación manual tradicional (turno diurno de 8h de lunes a sábado)
    z_manual = 0.0
    ord_sem_man = 0.0
    consec_man = 0
    plan_manual = [-1] * n_dias
    for d in range(n_dias):
        if d % 7 == 0:
            ord_sem_man = 0.0
        h_o, _, v_diurno = eval_matriz[d][0]
        if es_dom_o_festivo(fechas[d]) or consec_man >= 6 or (ord_sem_man + h_o > limite_semanal_ord):
            consec_man = 0
            plan_manual[d] = -1
        else:
            consec_man += 1
            ord_sem_man += h_o
            plan_manual[d] = 0
            z_manual += v_diurno

    return fechas, eval_matriz, mejor_plan, mejor_z, plan_manual, z_manual, nodos


if __name__ == "__main__":
    fechas, matriz, plan_opt, z_opt, plan_man, z_man, nodos = resolver_ple_branch_and_bound()
    print("=" * 88)
    print("MODELO PLE DE OPTIMIZACIÓN QUINCENAL DE TURNOS — MI NÓMINA (2026-11-01 a 2026-11-15)")
    print("=" * 88)
    print(f"{'Día':<5} {'Fecha':<12} {'Dom/Fest':<10} {'Turno Óptimo Asignado (x_ds=1)':<35} {'H.Ord':<7} {'Neto COP':>12}")
    print("-" * 88)
    for d, f in enumerate(fechas):
        s = plan_opt[d]
        tag = "SÍ (+90%)" if es_dom_o_festivo(f) else "No"
        if s == -1:
            print(f"D{d+1:<4} {f.isoformat():<12} {tag:<10} {'[DESCANSO OBLIGATORIO]':<35} {'0.0':<7} {'$0':>12}")
        else:
            t = CATALOGO_TURNOS[s]
            h_o, h_e, v = matriz[d][s]
            print(f"D{d+1:<4} {f.isoformat():<12} {tag:<10} {t.nombre:<35} {h_o:<7.1f} ${v:>11,.0f}")
    print("-" * 88)
    ganancia = z_opt - z_man
    pct = (ganancia / z_man) * 100.0 if z_man > 0 else 0.0
    print(f"Ingreso Neto Óptimo PLE (Z*):        ${z_opt:>12,.0f} COP")
    print(f"Ingreso Neto Asignación Manual:      ${z_man:>12,.0f} COP")
    print(f"Ganancia Neta Adicional (Delta Z):  +${ganancia:>12,.0f} COP (+{pct:.2f}%)")
    print(f"Nodos Explorados (Branch & Bound):   {nodos}")
    print("=" * 88)
