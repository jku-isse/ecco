package at.jku.mevss.ppu.simulation;

import at.jku.mevss.ppu.BooleanContainer;
import at.jku.mevss.ppu.ExtBooleanSupplier;
import at.jku.mevss.ppu.PPUUtils;
import at.jku.mevss.ppu.control.VariablePool;
import at.jku.mevss.ppu.j6.Function;
import at.jku.mevss.ppu.j6.Optional;
import at.jku.mevss.ppu.j6.Supplier;
import at.jku.mevss.ppu.simulation.machine.MachineSimulation;
import at.jku.mevss.ppu.simulation.machine.parts.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import static at.jku.mevss.ppu.simulation.Constants.*;

/**
 * Created by Florian on 28.03.2017.
 */
public class SimulationConfig_v5 {
    public static Function<MachineSimulation, List<SimulationComponent>> getConfig() {
        return new Function<MachineSimulation, List<SimulationComponent>>() {
            @Override
            public List<SimulationComponent> apply(MachineSimulation ms) {

                final VariablePool variablePool = ms.variablePool;
                final ElementStack stack = new ElementStack(100 + GLOBAL_OFFSET_X, 60 + GLOBAL_OFFSET_Y);
                {
                    final JButton addStack = new JButton("Add");
                    final String COMPONENT_NAME = "StackAdd";
                    addStack.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (!VariablePool.getInstance().isMachineTurnedOn()) {
                                Color c = PPUUtils.getRandomElement(ElementStack.allowedColors);
                                stack.availableElements.add(new Element(c));
                            } else {
                                VariablePool.getInstance().alarm(COMPONENT_NAME, "Cannot add element while machine is running!");
                            }
                        }
                    });

                    ms.add(addStack);
                    addStack.setBounds(82 + GLOBAL_OFFSET_X, 30 + GLOBAL_OFFSET_Y, 60, 25);
                }
                ArrayList<SimulationComponent> machineParts = new ArrayList<SimulationComponent>();

                machineParts.addAll(getHandoverZone1(ms));
                {
                    {
                        machineParts.addAll(getVerticalDownOutputRamp(ms, ms.handOverZone2, 0));
                        machineParts.addAll(getHandoverZone2(ms));

                    }
                }

                machineParts.addAll(getStamp(ms));
                machineParts.addAll(getHandoverZone3(ms));
                machineParts.addAll(getStackCylinder(ms, stack));

                {
                    Map<Integer, HandOverZone> map = new TreeMap<Integer, HandOverZone>();
                    map.put(90, ms.handOverZone3);
                    map.put(180, ms.handOverZone2);
                    map.put(270, ms.handOverZone1);
                    machineParts.addAll(getCrane(ms, map));
                }

                machineParts.add(stack);

                return machineParts;
            }
        };
    }


    public static List<SimulationComponent> getVerticalDownOutputRamp(MachineSimulation ms, HandOverZone handOverZone, int yOffset) {
        return getVerticalDownOutputRamp(ms, handOverZone, ms.variablePool.outputFullSingleRamp, yOffset);
    }

    public static List<SimulationComponent> getVerticalDownOutputRamp(MachineSimulation ms, HandOverZone handOverZone, BooleanContainer booleanContainer, int yOffset) {
        return getVerticalDownOutputRamp(ms, handOverZone, booleanContainer, 271 + GLOBAL_OFFSET_X, 197 + yOffset + GLOBAL_OFFSET_Y);
    }

    public static List<SimulationComponent> getVerticalDownOutputRamp(final MachineSimulation ms, HandOverZone handOverZone, final BooleanContainer booleanContainer, int xPos, int yPos) {
        int size = 3;
        final AbstractOutputRamp outputRamp = new VerticalDownOutputRamp(xPos, yPos, size, handOverZone);
        {
            ms.setLayout(null);
            ms.add(getTakeButton(outputRamp, xPos - 10, yPos + 10 + AbstractOutputRamp.getLength(size)));
        }
        return Arrays.asList(
                outputRamp,
                new Sensor(xPos + 50, yPos + 80, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = booleanContainer.setAsBoolean(outputRamp.isFull());
                        ms.variablePool.outPutFullChanged();
                        return !r;
                    }
                }) // Is there any place left in the output
        );
    }

    private static JButton getTakeButton(final AbstractOutputRamp outputRamp, int xPos, int yPos) {
        final JButton takeButton = new JButton("Take");
        takeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (outputRamp.findFreeIndex() == 0)
                    return;
                if (outputRamp.offset[0] != outputRamp.shouldOffset[0])
                    return;
                for (int i = 1; i < outputRamp.elements.length; i++) {
                    outputRamp.elements[i - 1] = outputRamp.elements[i];
                    outputRamp.offset[i - 1] = outputRamp.offset[i];
                }
                outputRamp.elements[outputRamp.elements.length - 1] = null;
                outputRamp.offset[outputRamp.offset.length - 1] = outputRamp.getDefaultOffset();
            }
        });
        takeButton.setBounds(xPos, yPos, 70, 25);
        return takeButton;
    }

    public static List<SimulationComponent> getCrane(final MachineSimulation ms, Map<Integer, HandOverZone> handOverZones) {
        int baseX = 275 + GLOBAL_OFFSET_X, baseY = 65 + GLOBAL_OFFSET_Y;
        final Crane crane = new Crane(baseX, baseY);
        crane.addHandOverZones(handOverZones);
        return Arrays.asList(
                crane,
                new Sensor(baseX + 40, baseY + 40, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = crane.curDegrees == 180;
                        ms.variablePool.setSensorCraneSixActive(r);
                        return r;
                    }
                }),
                new Sensor(baseX - 15, baseY - 25, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = crane.curDegrees == 270;
                        ms.variablePool.setSensorCraneNineAcive(r);
                        return r;

                    }
                }),
                new Sensor(baseX + 40, baseY - 25, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = crane.curDegrees == 90;
                        ms.variablePool.setSensorCraneThreeActive(r);
                        return r;
                    }
                }),
                new Sensor(baseX + 10, baseY - 15, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = crane.carriedElement != null && crane.carriedElement.isMetallic();
                        ms.variablePool.setSensorCraneHasSomethingMetallicInHand(r);
                        return r;
                    }
                }),
                new Sensor(baseX + 10, baseY + 10, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = crane.carriedElement != null;
                        ms.variablePool.setSensorCraneHasSomethingInHand(r);
                        return r;
                    }
                })
        );
    }

    public static List<SimulationComponent> getHandoverZone1(final MachineSimulation ms) {
        return Arrays.asList(
                new Sensor(155 + GLOBAL_OFFSET_X, 20 + GLOBAL_OFFSET_Y, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        Optional<Element> peek = ms.handOverZone1.peek();
                        if (!peek.isPresent()) {
                            ms.variablePool.setHandOverZone1Metallic(false);
                            return false;
                        }
                        Element e = peek.get();
                        boolean r = e.isMetallic();
                        ms.variablePool.setHandOverZone1Metallic(r);
                        return r;
                    }
                }),
                new Sensor(155 + GLOBAL_OFFSET_X, 40 + GLOBAL_OFFSET_Y, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = ms.handOverZone1.isFull();
                        ms.variablePool.setHandOverZone1Active(r);
                        return r;
                    }
                }),
                ms.handOverZone1
        );
    }

    public static List<SimulationComponent> getHandoverZone2(final MachineSimulation ms) {
        return Arrays.asList(
                new Sensor(320 + GLOBAL_OFFSET_X, 205 + GLOBAL_OFFSET_Y, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = ms.handOverZone2.isFull();
                        ms.variablePool.setHandOverZone2Active(r);
                        return r;
                    }
                }),
                ms.handOverZone2
        );
    }

    public static List<SimulationComponent> getStackCylinder(final MachineSimulation ms, ElementStack stack) {
        final StackCylinder stackCylinder = new StackCylinder(15 + GLOBAL_OFFSET_X, 65 + GLOBAL_OFFSET_Y, stack, ms.handOverZone1);
        return Arrays.asList(
                stackCylinder,
                new Sensor(25 + GLOBAL_OFFSET_X, 115 + GLOBAL_OFFSET_Y, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = stackCylinder.curWithdrawalOffset <= StackCylinder.MIN_WITHDRAWAL_OFFSET;
                        ms.variablePool.setStackPushWithdrawn(r);
                        return r;
                    }
                }),
                new Sensor(110 + GLOBAL_OFFSET_X, 115 + GLOBAL_OFFSET_Y, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = stackCylinder.curWithdrawalOffset >= StackCylinder.MAX_WITHDRAWAL_OFFSET;
                        ms.variablePool.setStackPushExtracted(r);
                        return r;
                    }
                })
        );
    }

    public static List<SimulationComponent> getHandoverZone3(final MachineSimulation ms) {
        return Arrays.asList(
                new Sensor(415 + GLOBAL_OFFSET_X, 40 + GLOBAL_OFFSET_Y, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = ms.handOverZone3.isFull();
                        ms.variablePool.setHandOverZone3Active(r);
                        return r;
                    }
                }),
                ms.handOverZone3
        );
    }

    public static List<SimulationComponent> getStamp(final MachineSimulation ms) {
        final int baseX = 415 + GLOBAL_OFFSET_X, baseY = 40 + GLOBAL_OFFSET_Y;
        final Stamp stamp = new Stamp(baseX, baseY, ms.handOverZone3);
        return Arrays.asList(
                stamp,
                new Sensor(baseX, baseY + 90, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = stamp.getCurItemContainerOffset() == Stamp.OFFSET_ITEM_CONTAINER_MAX;
                        ms.variablePool.setStampPushExtracted(r);
                        return r;
                    }
                }),
                new Sensor(baseX + 120, baseY + 90, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = stamp.getCurItemContainerOffset() == Stamp.OFFSET_ITEM_CONTAINER_MIN;
                        ms.variablePool.setStampPushWithdrawn(r);
                        return r;
                    }
                }),
                new Sensor(new Supplier<Integer>() {
                    @Override
                    public Integer get() {
                        return baseX + 120 - stamp.getCurItemContainerOffset() - SIZE_HALF_HANDOVERZONE;
                    }
                }, baseY + 105, new ExtBooleanSupplier() {
                    @Override
                    public Boolean get() {
                        boolean r = stamp.stackPushHasElement();
                        ms.variablePool.setStampPushItemInHand(r);
                        return r;
                    }
                })
        );
    }
}
