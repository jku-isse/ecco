package at.jku.mevss.ppu.control;

/**
 * Created by Florian on 23.03.2017.
 */
public class StackPushControl implements ControlComponent {

    private enum AirPressureState {
        ON, OFF
        //#if stackCylinderDetectsEmptyStack
        , WAIT_HANDOVERZONE, PANIC
        //#endif
    }


    private AirPressureState state = AirPressureState.OFF;
    //#if stackCylinderDetectsEmptyStack
    private static final String NAME = "StackPush";
    private boolean panicSent = false, crane9SensorWasActive = false;
    //#endif

    @Override
    public void control(long time) {
        VariablePool variablePool = VariablePool.getInstance();
        if (!variablePool.isMachineTurnedOn()) {
            if (state != AirPressureState.OFF)
                state = AirPressureState.OFF;
            //#if stackCylinderDetectsEmptyStack
            panicSent = false;
            //#endif
        } else switch (state) {
            //#if stackCylinderDetectsEmptyStack
            case WAIT_HANDOVERZONE:
                if (variablePool.isHandOverZone1Active())
                    state = AirPressureState.OFF;
                else if (!variablePool.isOutputFull() && variablePool.isSensorCraneHasSomethingInHand() && crane9SensorWasActive)
                    state = AirPressureState.OFF;
                else if (variablePool.isStackPushWithdrawn())
                    state = AirPressureState.PANIC;
                break;
            case PANIC:
                if (!panicSent) {
                    //#if alarm
                    variablePool.alarm(NAME, "No element in stack!");
                    //#endif
                    panicSent = true;
                }
                break;
            //#endif
            default:
                // #ifdef somefeature.1
                if (variablePool.isStackPushWithdrawn() && !variablePool.isHandOverZone1Active())
                    state = AirPressureState.ON;
                else if (variablePool.isStackPushExtracted()) {
                    //#if stackCylinderDetectsEmptyStack
                    state = AirPressureState.WAIT_HANDOVERZONE;
                    //#endif
                    //#if !stackCylinderDetectsEmptyStack
                    state = AirPressureState.OFF;
                    //#endif
                    //#endif
                    //#if somefeature.2 && !stackCylinderDetectsEmptyStack
                    state = AirPressureState.OFF2;
                    //#endif
                }
                break;
        }

        //#if stackCylinderDetectsEmptyStack
        crane9SensorWasActive = variablePool.isSensorCraneNineAcive();
        //#endif

        switch (state) {
            case ON:
                variablePool.setStackCylinderPushEnabled(true);
                break;
            default:
                variablePool.setStackCylinderPushEnabled(false);
        }
    }
}
//#endif