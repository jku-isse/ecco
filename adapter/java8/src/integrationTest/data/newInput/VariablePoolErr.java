package at.jku.mevss.ppu.control;

import at.jku.mevss.ppu.BooleanContainer;
import at.jku.mevss.ppu.ExtBooleanSupplier;
import at.jku.mevss.ppu.j6.Consumer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static at.jku.mevss.ppu.simulation.GraphicUtils.*;

/**
 * Created by Florian on 16.03.2017.
 */
public class VariablePoolErr {

    //Engine control
    //#if crane
    private int craneVelocity = 0
    private boolean craneGrab = false;
    private boolean craneLetGo = false;

    public int getCraneVelocity() {
        return craneVelocity;
    }

    public void setCraneVelocity(int craneVelocity) {
        this.craneVelocity = craneVelocity;
    }

    public boolean isCraneGrab() {
        return craneGrab;
    }

    public void setCraneGrab(boolean craneGrab) {
        this.craneGrab = craneGrab;
    }

    public boolean isCraneLetGo() {
        return craneLetGo;
    }

    public void setCraneLetGo(boolean craneLetGo) {
        this.craneLetGo = craneLetGo;
    }

    //#endif
    //#if stackCylinder
    private boolean stackCylinderPushEnabled = false;

    public boolean isStackCylinderPushEnabled() {
        return stackCylinderPushEnable

        public void setStackCylinderPushEnabled ( boolean stackCylinderPushEnabled){
            this.stackCylinderPushEnabled = stackCylinderPushEnabled;
        }

        //#endif
        //#if stamp
        private int stampCylinderMovementVelocity = 0;
        private boolean stampCylinderTakeElement = false;
        private boolean stampCylinderPutElement = false;

        public int getStampCylinderMovementVelocity () {
            return stampCylinderMovementVelocity;
        }

        public void setStampCylinderMovementVelocity ( int stampCylinderMovementVelocity){
            this.stampCylinderMovementVelocity = stampCylinderMovementVelocity;
        }

        public boolean isStampCylinderTakeElement () {
            return stampCylinderTakeElement;
        }

        public void setStampCylinderTakeElement ( boolean stampCylinderTakeElement){
            this.stampCylinderTakeElement = stampCylinderTakeElement;
        }

        public boolean isStampCylinderPutElement () {
            return stampCylinderPutElement;
        }

        public void setStampCylinderPutElement ( boolean stampCylinderPutElement){
            this.stampCylinderPutElement = stampCylinderPutElement;
        }

        //#endif
        //#if sorting
        public Map<Color, BooleanContainer> sortingCylinderPushActive;
        //#endif

        //Sensor data
        //#if crane
        private boolean sensorCraneNineAcive = false, sensorCraneSixActive = false, sensorCraneHasSomethingInHand = false;

        public boolean isSensorCraneNineAcive () {
            return sensorCraneNineAcive;
        }

        public void setSensorCraneNineAcive ( boolean sensorCraneNineAcive){
            this.sensorCraneNineAcive = sensorCraneNineAcive;
        }

        public boolean isSensorCraneSixActive () {
            return sensorCraneSixActive;
        }

        public void setSensorCraneSixActive ( boolean sensorCraneSixActive){
            this.sensorCraneSixActive = sensorCraneSixActive;
        }

        public boolean isSensorCraneHasSomethingInHand () {
            return sensorCraneHasSomethingInHand;
        }

        public void setSensorCraneHasSomethingInHand ( boolean sensorCraneHasSomethingInHand){
            this.sensorCraneHasSomethingInHand = sensorCraneHasSomethingInHand;
        }

        //#if stamp
        private boolean sensorCraneThreeActive = false;

        public boolean isSensorCraneThreeActive () {
            return sensorCraneThreeActive;
        }

        public void setSensorCraneThreeActive ( boolean sensorCraneThreeActive){
            this.sensorCraneThreeActive = sensorCraneThreeActive;
        }

        //#endif
        //#if woodenMetallicPieces
        private boolean sensorCraneHasSomethingMetallicInHand = false;

        public boolean isSensorCraneHasSomethingMetallicInHand () {
            return sensorCraneHasSomethingMetallicInHand;
        }

        public void setSensorCraneHasSomethingMetallicInHand ( boolean sensorCraneHasSomethingMetallicInHand){
            this.sensorCraneHasSomethingMetallicInHand = sensorCraneHasSomethingMetallicInHand;
        }

        //#endif
        //#endif
        //#if stamp
        private boolean stampPushWithdrawn = true;
        private boolean stampPushExtracted = false;
        private boolean stampPushItemInHand = false;

        public boolean isStampPushWithdrawn () {
            return stampPushWithdrawn;
        }

        public void setStampPushWithdrawn ( boolean stampPushWithdrawn){
            this.stampPushWithdrawn = stampPushWithdrawn;
        }

        public boolean isStampPushExtracted () {
            return stampPushExtracted;
        }

        public void setStampPushExtracted ( boolean stampPushExtracted){
            this.stampPushExtracted = stampPushExtracted;
        }

        public boolean isStampPushItemInHand () {
            return stampPushItemInHand;
        }

        public void setStampPushItemInHand ( boolean stampPushItemInHand){
            this.stampPushItemInHand = stampPushItemInHand;
        }

        //#if craneQuickWorkPiece
        private boolean handOverZone1Metallic = false;

        public boolean isHandOverZone1Metallic () {
            return handOverZone1Metallic;
        }

        public void setHandOverZone1Metallic ( boolean handOverZone1Metallic){
            this.handOverZone1Metallic = handOverZone1Metallic;
        }

        //#endif
        //#endif
        //#if sorting
        public Map<Color, BooleanContainer> sortingCylinderWithdrawn;
        public Map<Color, BooleanContainer> sortingCylinderExtracted;
        public Map<Color, BooleanContainer> sortingColorSensorResults;
        //#endif

        //#if stackCylinder
        private boolean stackPushWithdrawn = true;
        private boolean stackPushExtracted = false;

        public boolean isStackPushWithdrawn () {
            return stackPushWithdrawn;
        }

        public void setStackPushWithdrawn ( boolean stackPushWithdrawn){
            this.stackPushWithdrawn = stackPushWithdrawn;
        }

        public boolean isStackPushExtracted () {
            return stackPushExtracted;
        }

        public void setStackPushExtracted ( boolean stackPushExtracted){
            this.stackPushExtracted = stackPushExtracted;
        }

        //#endif

        private boolean handOverZone1Active = false;
        private boolean handOverZone2Active = false;

        public boolean isHandOverZone1Active () {
            return handOverZone1Active;
        }

        public void setHandOverZone1Active ( boolean handOverZone1Active){
            this.handOverZone1Active = handOverZone1Active;
        }

        public boolean isHandOverZone2Active () {
            return handOverZone2Active;
        }

        public void setHandOverZone2Active ( boolean handOverZone2Active){
            this.handOverZone2Active = handOverZone2Active;
        }

        //#if stamp
        private boolean handOverZone3Active = false;

        public boolean isHandOverZone3Active () {
            return handOverZone3Active;
        }

        public void setHandOverZone3Active ( boolean handOverZone3Active){
            this.handOverZone3Active = handOverZone3Active;
        }

        //#endif
        private ExtBooleanSupplier outputFull;

        public boolean isOutputFull () {
            return outputFull.get();
        }

        public void outPutFullChanged () {
            //TODO sync with KEBA code
        }

        //#if ramp
        public BooleanContainer outputFullSingleRamp;
        //#if sorting
        public Map<Color, BooleanContainer> outputFullSensors; //FIXME check occurence
        //#endif
        //#endif


        private boolean machineTurnedOn = false;

        public boolean isMachineTurnedOn () {
            return machineTurnedOn;
        }

        public void setMachineTurnedOn ( boolean machineTurnedOn){
            this.machineTurnedOn = machineTurnedOn;
        }

        private static VariablePool _instance = null;

        public static VariablePool getInstance () {
            if (_instance == null)
                _instance = new VariablePool();
            return _instance;
        }


    private VariablePool() {
            //#if sorting
            java.util.List<Color> colors;
            //#if woodenMetallicPieces
            colors = Collections.singletonList(BLUE_GREY);
            //#else
            colors = Arrays.asList(PINK, GREEN, ORANGE);
            //#endif
            sortingCylinderPushActive = getInitialMap(colors);
            sortingCylinderExtracted = getInitialMap(colors);
            sortingCylinderWithdrawn = getInitialMap(colors);
            sortingColorSensorResults = getInitialMap(colors);
            //#endif
            //#if ramp
            outputFullSingleRamp = new BooleanContainer(false);
            //#if !sorting
            outputFull = outputFullSingleRamp;
            //#else
            {
                outputFullSensors = getInitialMap(colors);
                outputFull = new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        if (outputFullSingleRamp.getAsBoolean())
                            return true;
                        for (BooleanContainer bc : outputFullSensors.values())
                            if (bc.getAsBoolean())
                                return true;
                        return false;
                    }
                };
            }
            //#endif
            //#endif
        }

        //#if sorting
        private Map<Color, BooleanContainer> getInitialMap (java.util.List < Color > colors) {
            Map<Color, BooleanContainer> map = new HashMap<Color, BooleanContainer>(colors.size());
            for (Color c : colors) {
                map.put(c, new BooleanContainer(false));
            }
            return map;
        }
        //#endif

        //#if alarm
        //Alarm control
        public Consumer<String> alarmOutput = new Consumer<String>() {
            @Override
            public void accept(String e) {
                System.out.println(e);
            }
        };

        public void alarm (String component, String message){
            alarmOutput.accept(String.format("[ %s ]         %s", component, message));
        }
        //#endif
    }