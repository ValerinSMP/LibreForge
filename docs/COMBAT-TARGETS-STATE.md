# Contexto opt-in de objetivos — 21/10/2026

Solo EcoEnchantLevel implementa CombatTargetHolder. Se reutiliza el camino de efectos, TriggerData/originalPlayer y scopes para áreas/daño/controles diferidos y efímeros. Otros holders no reciben una política nueva. Contrato y manifiesto: EcoEnchants/docs/COMBAT-TARGETS-HANDOFF.md y delivery/combat-targets-20261021.

Build y verificaciones finales: consultar VALIDATION.json/runtime-manifest.json de la entrega; sin instalación, activación ni juego. BoundCounter/ContextualAccumulator y STATE.md ajenos se conservan y quedan fuera del compuesto y del commit. No hay escrituras SQL, lifecycle ni cambios a consumidores de compra/XP.
