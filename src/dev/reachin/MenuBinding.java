package dev.reachin;

/** Per-menu Reach-In state. Lives on the menu itself via {@link ReachInMenu}. */
public class MenuBinding {
    public final ShulkerView view = new ShulkerView();

    /** Menu index of the first grid slot, or -1 before the slots are appended. */
    public int base = -1;

    /** Menu index of the shulker box being viewed, or -1 when unbound. */
    public int hostIndex = -1;

    /**
     * Set while vanilla's own moveItemStackTo is running, so the grid refuses
     * placement. ChestMenu bounds its quick-move range with slots.size(), which
     * now includes the grid; without this, chest items spill into the shulker.
     */
    public boolean autoGuard = false;

    public boolean installed() {
        return base >= 0;
    }

    public boolean bound() {
        return hostIndex >= 0 && view.valid();
    }
}
