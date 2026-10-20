# Native mining opt-in — 2026-10-20

Único cambio propio: AttributeEffect.onEnable admite args.transient:true para modificadores temporales nativos; default/firma/identificador/retirada anteriores intactos. Consumidor inicial: EcoEnchants Ritmo minero. No cambia holders de otros consumidores sin opt-in.

LF155 tests JVM y build personal limitado pasaron. Runtime compuesto selectivamente sobre requirement-lore (graft onEnable); SHA256 b0a0ab399620a3d37b37c16a0ae33f5d20a3a533378a9bb68984b56435971d77. API existente preservada, resto de entradas del baseline intacto. No instalado/lifecycle/gameplay. Contrato detallado: EcoEnchants/docs/NATIVE-MINING-HANDOFF.md. STATE/BoundCounter/ContextualAccumulator ajenos sin tocar.
