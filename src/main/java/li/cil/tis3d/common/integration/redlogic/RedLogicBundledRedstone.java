package li.cil.tis3d.common.integration.redlogic;

import cpw.mods.fml.common.Optional;
import li.cil.tis3d.api.machine.Casing;
import li.cil.tis3d.api.machine.Face;
import li.cil.tis3d.api.module.BundledRedstone;
import li.cil.tis3d.api.module.Module;
import mods.immibis.redlogic.api.wiring.IBundledEmitter;
import mods.immibis.redlogic.api.wiring.IBundledUpdatable;

@Optional.InterfaceList({
        @Optional.Interface(iface = "mods.immibis.redlogic.api.wiring.IBundledEmitter", modid = ProxyRedLogic.MOD_ID),
        @Optional.Interface(iface = "mods.immibis.redlogic.api.wiring.IBundledUpdatable", modid = ProxyRedLogic.MOD_ID)
})
public interface RedLogicBundledRedstone extends IBundledEmitter, IBundledUpdatable {
    Casing getCasing();

    @Optional.Method(modid = ProxyRedLogic.MOD_ID)
    @Override
    default byte[] getBundledCableStrength(final int blockFace, final int toDirection) {
        final Module module = getCasing().getModule(Face.VALUES[toDirection]);
        if (module instanceof BundledRedstone) {
            final BundledRedstone bundledRedstone = (BundledRedstone) module;

            final byte[] signal = new byte[16];
            for (int channel = 0; channel < signal.length; channel++) {
                signal[channel] = (byte) bundledRedstone.getBundledRedstoneOutput(channel);
            }
            return signal;
        }
        return null;
    }

    @Optional.Method(modid = ProxyRedLogic.MOD_ID)
    @Override
    default void onBundledInputChanged() {
        getCasing().markDirty();
    }
}
