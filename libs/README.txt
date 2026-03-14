# Drop the Automobility jar here before building.
# e.g. automobility-0_4_2_1_20_1-fabric.jar
#
# The jar is used only for compilation — it is NOT bundled into Momentum's output.
# Players must have Automobility installed separately in their mods/ folder.

#
#AutomobileEntityMixin.java — the core mixin, hooks into Automobility's AutomobileEntity #class
#
#momentum$replaceCoastDecay — coast decay (@Redirect on AUtils.zero in movementTick)
#momentum$scaleAcceleration — acceleration scale (@ModifyArg on calculateAcceleration in #movementTick)
#momentum$steeringRampRate — steering ramp (@ModifyConstant on 0.42f in steeringTick)
#momentum$applyUndersteer — understeer (@ModifyArg on AUtils.shift ordinal 1 in #postMovementTick)
#AutomobileHudMixin.java — suppresses Automobility's built-in speedometer so ours can #replace it (@Inject cancel on renderSpeedometer)
#
#MomentumHud.java — draws the custom speed bar and km/h readout, and the optional debug #overlay
#
#MomentumConfig.java — reads/writes momentum.json; all the tunable values live here
#
#SteeringDebugAccessor.java — a small interface that lets the HUD read private #Automobility fields (steering, hSpeed, angularSpeed, drifting) without being in the mixin #package
#
#momentum.mixins.json — registers both mixins with Fabric's mixin system (required for #them to load)
