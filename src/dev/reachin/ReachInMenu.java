package dev.reachin;

/** Implemented on every AbstractContainerMenu by the Reach-In mixin. */
public interface ReachInMenu {
    MenuBinding reachin$binding();

    /** Appends the 27 grid slots. Idempotent. */
    void reachin$install();

    /** Points the grid at the shulker box in the given menu slot. */
    boolean reachin$bind(int hostIndex);

    void reachin$unbind();
}
