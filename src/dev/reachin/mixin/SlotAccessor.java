package dev.reachin.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Slot.x and Slot.y are final. The grid moves its cells every frame. */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void reachin$setX(int x);

    @Mutable
    @Accessor("y")
    void reachin$setY(int y);
}
