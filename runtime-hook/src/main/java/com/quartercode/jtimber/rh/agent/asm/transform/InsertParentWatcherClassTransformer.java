/*
 * This file is part of JTimber.
 * Copyright (c) 2015 QuarterCode <http://quartercode.com/>
 *
 * JTimber is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JTimber is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JTimber. If not, see <http://www.gnu.org/licenses/>.
 */

package com.quartercode.jtimber.rh.agent.asm.transform;

import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import com.quartercode.jtimber.rh.agent.asm.ClassMetadata;
import com.quartercode.jtimber.rh.agent.asm.MetadataAwareClassVisitor;
import com.quartercode.jtimber.rh.agent.asm.util.ASMUtils;

/**
 * The {@link ClassVisitor} which adds so called "parent watchers" to nodes in order to track the parents of parent-aware objects.
 * Note that it transforms all classes that are fed into it.
 * Therefore, only node classes should be sent through it.
 */
public final class InsertParentWatcherClassTransformer extends MetadataAwareClassVisitor {

    /**
     * Creates a new insert parent watcher class transformer.
     * 
     * @param cv The class visitor to which this visitor delegates method calls. May be {@code null}.
     * @param metadata The {@link ClassMetadata} object the transformer uses to retrieve metadata about the processed class.
     */
    public InsertParentWatcherClassTransformer(ClassVisitor cv, ClassMetadata metadata) {

        super(cv, metadata);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

        // Return an InsertParentWatcherMethodAdapter
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (mv != null) {
            mv = new InsertParentWatcherMethodAdapter(mv);
        }
        return mv;
    }

    /*
     * The method adapter internally used by the InsertParentWatcherClassAdapter.
     * It adds parent watcher instructions around each PUTFIELD instruction (for allowed fields).
     */
    private final class InsertParentWatcherMethodAdapter extends MethodVisitor {

        private InsertParentWatcherMethodAdapter(MethodVisitor mv) {

            super(ASM5, mv);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {

            /*
             * If this is a PUTFIELD instruction, the field isn't weak (no @Weak annotation), the field references an object and the field is located in this class,
             * add the parent watcher code around the instruction.
             * Note that the instructions inside this block make sure to reconstruct the original stack.
             */
            if (opcode == PUTFIELD && !metadata.weakFields.contains(name) && Type.getType(desc).getSort() == Type.OBJECT && owner.equals(metadata.classType.getInternalName())) {
                /*
                 * If a parent-aware object is already present in the field, remove "this" from its parents.
                 */

                // Push the old object from the accessed field
                super.visitVarInsn(ALOAD, 0);
                super.visitFieldInsn(GETFIELD, owner, name, desc);

                // Write a remove parent instruction set that uses the recently pushed "old" object
                ASMUtils.generateAddOrRemoveThisAsParent(mv, "removeParent");

                // Discard the "old" field value pushed earlier
                super.visitInsn(POP);

                /*
                 * If the new object is parent-aware (and not null), add this objects to its parents.
                 */

                // Write an add parent instruction set that uses the "new" object already on the stack
                // No popping is necessary afterwards because the "new" object will be used by the next instruction
                ASMUtils.generateAddOrRemoveThisAsParent(mv, "addParent");
            }

            // Write the actual field instruction by calling the next visitor
            super.visitFieldInsn(opcode, owner, name, desc);
        }

    }

}
