package com.egakh.familytree.breeding;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.Set;

/**
 * Copies the static breeding methods out of the running version's {@code AbstractHorse} bytecode. Loading
 * {@code AbstractHorse} itself needs Minecraft's registries, but these methods only use {@code Mth} and
 * {@code RandomSource}, so the copy runs the game's own instructions in a plain unit test.
 */
final class GameHorseCode {
    private static final String NAME = "com.egakh.familytree.breeding.GameHorseRules";
    private static final Set<String> METHODS = Set.of("createOffspringAttribute", "generateSpeed", "generateJumpStrength", "generateMaxHealth");

    private GameHorseCode() {}

    static Class<?> load() throws Exception {
        String resource = AbstractHorse.class.getName().replace('.', '/') + ".class";
        byte[] original;
        try (InputStream in = GameHorseCode.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing " + resource);
            original = in.readAllBytes();
        }
        ClassNode source = new ClassNode();
        new ClassReader(original).accept(source, 0);
        ClassNode copy = new ClassNode();
        copy.version = source.version;
        copy.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL;
        copy.name = NAME.replace('.', '/');
        copy.superName = "java/lang/Object";
        for (MethodNode method : source.methods) {
            if (!METHODS.contains(method.name) || (method.access & Opcodes.ACC_STATIC) == 0) continue;
            method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
            copy.methods.add(method);
        }
        if (copy.methods.size() != METHODS.size())
            throw new IllegalStateException("AbstractHorse has " + copy.methods.size() + " of " + METHODS + " in this version");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        copy.accept(writer);
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(GameHorseCode.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(NAME, bytes, 0, bytes.length);
            }
        }.define();
    }
}
