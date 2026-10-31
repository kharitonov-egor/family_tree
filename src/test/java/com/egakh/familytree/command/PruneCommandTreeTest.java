package com.egakh.familytree.command;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** QA B5 for prune: a requirement on the node turns a denied permission into vanilla's "Incorrect argument". */
class PruneCommandTreeTest {
    @Test void noPruneNodeHidesItselfFromAPlayer() {
        CommandNode<CommandSourceStack> prune = FamilyTreeCommand.pruneCommand().build();
        assertEquals(java.util.Set.of("deceased", "farm", "species"),
                prune.getChildren().stream().map(CommandNode::getName).collect(java.util.stream.Collectors.toSet()));
        assertNoRequirement(prune);
    }

    private static void assertNoRequirement(CommandNode<CommandSourceStack> node) {
        assertTrue(node.getRequirement().test(null), node.getName() + " has a requirement");
        for (CommandNode<CommandSourceStack> child : node.getChildren()) assertNoRequirement(child);
    }
}
