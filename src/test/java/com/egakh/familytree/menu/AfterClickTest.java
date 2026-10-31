package com.egakh.familytree.menu;

import net.minecraft.server.TickTask;
import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AfterClickTest {
    /** A server thread handling a packet outside any task, which is how Minecraft 1.21.10 and newer handle clicks. */
    private static final class PacketThread extends BlockableEventLoop<TickTask> {
        PacketThread() {
            //? if >=26.1 {
            super("familytree-test", false);
            //?} else {
            /*super("familytree-test");
            *///?}
        }

        @Override
        public TickTask wrapRunnable(Runnable runnable) {
            return new TickTask(0, runnable);
        }

        @Override
        protected boolean shouldRun(TickTask task) {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return Thread.currentThread();
        }

        void finishTick() {
            runAllTasks();
        }
    }

    @Test
    void executeRunsInsideThePacketThereSoMenusMustQueue() {
        PacketThread server = new PacketThread();
        List<String> order = new ArrayList<>();
        server.execute(() -> order.add("execute"));
        order.add("click handled");
        assertEquals(List.of("execute", "click handled"), order);
    }

    @Test
    void queuedActionsRunAfterTheClickPacket() {
        PacketThread server = new PacketThread();
        List<String> order = new ArrayList<>();
        Menus.queue(server, new TickTask(0, () -> order.add("menu action")));
        order.add("click handled");
        server.finishTick();
        assertEquals(List.of("click handled", "menu action"), order);
    }
}
