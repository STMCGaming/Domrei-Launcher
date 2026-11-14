/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 *  org.lwjgl.PointerBuffer
 *  org.lwjgl.opengl.GL
 *  org.lwjgl.opengl.GL32C
 *  org.lwjgl.system.NativeType
 */
package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.NativeType;

public class GL32
extends GL31 {
    public static final int GL_CONTEXT_PROFILE_MASK = 37158;
    public static final int GL_CONTEXT_CORE_PROFILE_BIT = 1;
    public static final int GL_CONTEXT_COMPATIBILITY_PROFILE_BIT = 2;
    public static final int GL_MAX_VERTEX_OUTPUT_COMPONENTS = 37154;
    public static final int GL_MAX_GEOMETRY_INPUT_COMPONENTS = 37155;
    public static final int GL_MAX_GEOMETRY_OUTPUT_COMPONENTS = 37156;
    public static final int GL_MAX_FRAGMENT_INPUT_COMPONENTS = 37157;
    public static final int GL_FIRST_VERTEX_CONVENTION = 36429;
    public static final int GL_LAST_VERTEX_CONVENTION = 36430;
    public static final int GL_PROVOKING_VERTEX = 36431;
    public static final int GL_QUADS_FOLLOW_PROVOKING_VERTEX_CONVENTION = 36428;
    public static final int GL_TEXTURE_CUBE_MAP_SEAMLESS = 34895;
    public static final int GL_SAMPLE_POSITION = 36432;
    public static final int GL_SAMPLE_MASK = 36433;
    public static final int GL_SAMPLE_MASK_VALUE = 36434;
    public static final int GL_TEXTURE_2D_MULTISAMPLE = 37120;
    public static final int GL_PROXY_TEXTURE_2D_MULTISAMPLE = 37121;
    public static final int GL_TEXTURE_2D_MULTISAMPLE_ARRAY = 37122;
    public static final int GL_PROXY_TEXTURE_2D_MULTISAMPLE_ARRAY = 37123;
    public static final int GL_MAX_SAMPLE_MASK_WORDS = 36441;
    public static final int GL_MAX_COLOR_TEXTURE_SAMPLES = 37134;
    public static final int GL_MAX_DEPTH_TEXTURE_SAMPLES = 37135;
    public static final int GL_MAX_INTEGER_SAMPLES = 37136;
    public static final int GL_TEXTURE_BINDING_2D_MULTISAMPLE = 37124;
    public static final int GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY = 37125;
    public static final int GL_TEXTURE_SAMPLES = 37126;
    public static final int GL_TEXTURE_FIXED_SAMPLE_LOCATIONS = 37127;
    public static final int GL_SAMPLER_2D_MULTISAMPLE = 37128;
    public static final int GL_INT_SAMPLER_2D_MULTISAMPLE = 37129;
    public static final int GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE = 37130;
    public static final int GL_SAMPLER_2D_MULTISAMPLE_ARRAY = 37131;
    public static final int GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY = 37132;
    public static final int GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY = 37133;
    public static final int GL_DEPTH_CLAMP = 34383;
    public static final int GL_GEOMETRY_SHADER = 36313;
    public static final int GL_GEOMETRY_VERTICES_OUT = 36314;
    public static final int GL_GEOMETRY_INPUT_TYPE = 36315;
    public static final int GL_GEOMETRY_OUTPUT_TYPE = 36316;
    public static final int GL_MAX_GEOMETRY_TEXTURE_IMAGE_UNITS = 35881;
    public static final int GL_MAX_GEOMETRY_UNIFORM_COMPONENTS = 36319;
    public static final int GL_MAX_GEOMETRY_OUTPUT_VERTICES = 36320;
    public static final int GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS = 36321;
    public static final int GL_LINES_ADJACENCY = 10;
    public static final int GL_LINE_STRIP_ADJACENCY = 11;
    public static final int GL_TRIANGLES_ADJACENCY = 12;
    public static final int GL_TRIANGLE_STRIP_ADJACENCY = 13;
    public static final int GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS = 36264;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_LAYERED = 36263;
    public static final int GL_PROGRAM_POINT_SIZE = 34370;
    public static final int GL_MAX_SERVER_WAIT_TIMEOUT = 37137;
    public static final int GL_OBJECT_TYPE = 37138;
    public static final int GL_SYNC_CONDITION = 37139;
    public static final int GL_SYNC_STATUS = 37140;
    public static final int GL_SYNC_FLAGS = 37141;
    public static final int GL_SYNC_FENCE = 37142;
    public static final int GL_SYNC_GPU_COMMANDS_COMPLETE = 37143;
    public static final int GL_UNSIGNALED = 37144;
    public static final int GL_SIGNALED = 37145;
    public static final int GL_SYNC_FLUSH_COMMANDS_BIT = 1;
    public static final long GL_TIMEOUT_IGNORED = -1L;
    public static final int GL_ALREADY_SIGNALED = 37146;
    public static final int GL_TIMEOUT_EXPIRED = 37147;
    public static final int GL_CONDITION_SATISFIED = 37148;
    public static final int GL_WAIT_FAILED = 37149;

    protected GL32() {
        throw new UnsupportedOperationException();
    }

    public static void nglGetBufferParameteri64v(int target, int pname, long params) {
        GL32C.nglGetBufferParameteri64v((int)target, (int)pname, (long)params);
    }

    public static void glGetBufferParameteri64v(@NativeType(value="GLenum") int target, @NativeType(value="GLenum") int pname, @NativeType(value="GLint64 *") LongBuffer params) {
        GL32C.glGetBufferParameteri64v((int)target, (int)pname, (LongBuffer)params);
    }

    @NativeType(value="void")
    public static long glGetBufferParameteri64(@NativeType(value="GLenum") int target, @NativeType(value="GLenum") int pname) {
        return GL32C.glGetBufferParameteri64((int)target, (int)pname);
    }

    public static void nglDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        GL32C.nglDrawElementsBaseVertex((int)mode, (int)count, (int)type, (long)indices, (int)basevertex);
    }

    public static void glDrawElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLsizei") int count, @NativeType(value="GLenum") int type, @NativeType(value="void const *") long indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsBaseVertex((int)mode, (int)count, (int)type, (long)indices, (int)basevertex);
    }

    public static void glDrawElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLenum") int type, @NativeType(value="void const *") ByteBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsBaseVertex((int)mode, (int)type, (ByteBuffer)indices, (int)basevertex);
    }

    public static void glDrawElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="void const *") ByteBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsBaseVertex((int)mode, (ByteBuffer)indices, (int)basevertex);
    }

    public static void glDrawElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="void const *") ShortBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsBaseVertex((int)mode, (ShortBuffer)indices, (int)basevertex);
    }

    public static void glDrawElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="void const *") IntBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsBaseVertex((int)mode, (IntBuffer)indices, (int)basevertex);
    }

    public static void nglDrawRangeElementsBaseVertex(int mode, int start, int end, int count, int type, long indices, int basevertex) {
        GL32C.nglDrawRangeElementsBaseVertex((int)mode, (int)start, (int)end, (int)count, (int)type, (long)indices, (int)basevertex);
    }

    public static void glDrawRangeElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLuint") int start, @NativeType(value="GLuint") int end, @NativeType(value="GLsizei") int count, @NativeType(value="GLenum") int type, @NativeType(value="void const *") long indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawRangeElementsBaseVertex((int)mode, (int)start, (int)end, (int)count, (int)type, (long)indices, (int)basevertex);
    }

    public static void glDrawRangeElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLuint") int start, @NativeType(value="GLuint") int end, @NativeType(value="GLenum") int type, @NativeType(value="void const *") ByteBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawRangeElementsBaseVertex((int)mode, (int)start, (int)end, (int)type, (ByteBuffer)indices, (int)basevertex);
    }

    public static void glDrawRangeElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLuint") int start, @NativeType(value="GLuint") int end, @NativeType(value="void const *") ByteBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawRangeElementsBaseVertex((int)mode, (int)start, (int)end, (ByteBuffer)indices, (int)basevertex);
    }

    public static void glDrawRangeElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLuint") int start, @NativeType(value="GLuint") int end, @NativeType(value="void const *") ShortBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawRangeElementsBaseVertex((int)mode, (int)start, (int)end, (ShortBuffer)indices, (int)basevertex);
    }

    public static void glDrawRangeElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLuint") int start, @NativeType(value="GLuint") int end, @NativeType(value="void const *") IntBuffer indices, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawRangeElementsBaseVertex((int)mode, (int)start, (int)end, (IntBuffer)indices, (int)basevertex);
    }

    public static void nglDrawElementsInstancedBaseVertex(int mode, int count, int type, long indices, int primcount, int basevertex) {
        GL32C.nglDrawElementsInstancedBaseVertex((int)mode, (int)count, (int)type, (long)indices, (int)primcount, (int)basevertex);
    }

    public static void glDrawElementsInstancedBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLsizei") int count, @NativeType(value="GLenum") int type, @NativeType(value="void const *") long indices, @NativeType(value="GLsizei") int primcount, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsInstancedBaseVertex((int)mode, (int)count, (int)type, (long)indices, (int)primcount, (int)basevertex);
    }

    public static void glDrawElementsInstancedBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLenum") int type, @NativeType(value="void const *") ByteBuffer indices, @NativeType(value="GLsizei") int primcount, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsInstancedBaseVertex((int)mode, (int)type, (ByteBuffer)indices, (int)primcount, (int)basevertex);
    }

    public static void glDrawElementsInstancedBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="void const *") ByteBuffer indices, @NativeType(value="GLsizei") int primcount, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsInstancedBaseVertex((int)mode, (ByteBuffer)indices, (int)primcount, (int)basevertex);
    }

    public static void glDrawElementsInstancedBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="void const *") ShortBuffer indices, @NativeType(value="GLsizei") int primcount, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsInstancedBaseVertex((int)mode, (ShortBuffer)indices, (int)primcount, (int)basevertex);
    }

    public static void glDrawElementsInstancedBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="void const *") IntBuffer indices, @NativeType(value="GLsizei") int primcount, @NativeType(value="GLint") int basevertex) {
        GL32C.glDrawElementsInstancedBaseVertex((int)mode, (IntBuffer)indices, (int)primcount, (int)basevertex);
    }

    public static void nglMultiDrawElementsBaseVertex(int mode, long count, int type, long indices, int drawcount, long basevertex) {
        GL32C.nglMultiDrawElementsBaseVertex((int)mode, (long)count, (int)type, (long)indices, (int)drawcount, (long)basevertex);
    }

    public static void glMultiDrawElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLsizei const *") IntBuffer count, @NativeType(value="GLenum") int type, @NativeType(value="void const * const *") PointerBuffer indices, @NativeType(value="GLint *") IntBuffer basevertex) {
        GL32C.glMultiDrawElementsBaseVertex((int)mode, (IntBuffer)count, (int)type, (PointerBuffer)indices, (IntBuffer)basevertex);
    }

    public static void glProvokingVertex(@NativeType(value="GLenum") int mode) {
        GL32C.glProvokingVertex((int)mode);
    }

    public static void glTexImage2DMultisample(@NativeType(value="GLenum") int target, @NativeType(value="GLsizei") int samples, @NativeType(value="GLint") int internalformat, @NativeType(value="GLsizei") int width, @NativeType(value="GLsizei") int height, @NativeType(value="GLboolean") boolean fixedsamplelocations) {
        GL32C.glTexImage2DMultisample((int)target, (int)samples, (int)internalformat, (int)width, (int)height, (boolean)fixedsamplelocations);
    }

    public static void glTexImage3DMultisample(@NativeType(value="GLenum") int target, @NativeType(value="GLsizei") int samples, @NativeType(value="GLint") int internalformat, @NativeType(value="GLsizei") int width, @NativeType(value="GLsizei") int height, @NativeType(value="GLsizei") int depth, @NativeType(value="GLboolean") boolean fixedsamplelocations) {
        GL32C.glTexImage3DMultisample((int)target, (int)samples, (int)internalformat, (int)width, (int)height, (int)depth, (boolean)fixedsamplelocations);
    }

    public static void nglGetMultisamplefv(int pname, int index, long val) {
        GL32C.nglGetMultisamplefv((int)pname, (int)index, (long)val);
    }

    public static void glGetMultisamplefv(@NativeType(value="GLenum") int pname, @NativeType(value="GLuint") int index, @NativeType(value="GLfloat *") FloatBuffer val) {
        GL32C.glGetMultisamplefv((int)pname, (int)index, (FloatBuffer)val);
    }

    @NativeType(value="void")
    public static float glGetMultisamplef(@NativeType(value="GLenum") int pname, @NativeType(value="GLuint") int index) {
        return GL32C.glGetMultisamplef((int)pname, (int)index);
    }

    public static void glSampleMaski(@NativeType(value="GLuint") int index, @NativeType(value="GLbitfield") int mask) {
        GL32C.glSampleMaski((int)index, (int)mask);
    }

    public static void glFramebufferTexture(@NativeType(value="GLenum") int target, @NativeType(value="GLenum") int attachment, @NativeType(value="GLuint") int texture, @NativeType(value="GLint") int level) {
        GL32C.glFramebufferTexture((int)target, (int)attachment, (int)texture, (int)level);
    }

    @NativeType(value="GLsync")
    public static long glFenceSync(@NativeType(value="GLenum") int condition, @NativeType(value="GLbitfield") int flags) {
        return GL32C.glFenceSync((int)condition, (int)flags);
    }

    public static boolean nglIsSync(long sync) {
        return GL32C.nglIsSync((long)sync);
    }

    @NativeType(value="GLboolean")
    public static boolean glIsSync(@NativeType(value="GLsync") long sync) {
        return GL32C.glIsSync((long)sync);
    }

    public static void nglDeleteSync(long sync) {
        GL32C.nglDeleteSync((long)sync);
    }

    public static void glDeleteSync(@NativeType(value="GLsync") long sync) {
        GL32C.glDeleteSync((long)sync);
    }

    public static int nglClientWaitSync(long sync, int flags, long timeout) {
        return GL32C.nglClientWaitSync((long)sync, (int)flags, (long)timeout);
    }

    @NativeType(value="GLenum")
    public static int glClientWaitSync(@NativeType(value="GLsync") long sync, @NativeType(value="GLbitfield") int flags, @NativeType(value="GLuint64") long timeout) {
        return GL32C.glClientWaitSync(sync, flags, timeout);
    }

    public static void nglWaitSync(long sync, int flags, long timeout) {
        GL32C.nglWaitSync((long)sync, (int)flags, (long)timeout);
    }

    public static void glWaitSync(@NativeType(value="GLsync") long sync, @NativeType(value="GLbitfield") int flags, @NativeType(value="GLuint64") long timeout) {
        GL32C.glWaitSync((long)sync, (int)flags, (long)timeout);
    }

    public static void nglGetInteger64v(int pname, long params) {
        GL32C.nglGetInteger64v((int)pname, (long)params);
    }

    public static void glGetInteger64v(@NativeType(value="GLenum") int pname, @NativeType(value="GLint64 *") LongBuffer params) {
        GL32C.glGetInteger64v((int)pname, (LongBuffer)params);
    }

    @NativeType(value="void")
    public static long glGetInteger64(@NativeType(value="GLenum") int pname) {
        return GL32C.glGetInteger64((int)pname);
    }

    public static void nglGetInteger64i_v(int pname, int index, long params) {
        GL32C.nglGetInteger64i_v((int)pname, (int)index, (long)params);
    }

    public static void glGetInteger64i_v(@NativeType(value="GLenum") int pname, @NativeType(value="GLuint") int index, @NativeType(value="GLint64 *") LongBuffer params) {
        GL32C.glGetInteger64i_v((int)pname, (int)index, (LongBuffer)params);
    }

    @NativeType(value="void")
    public static long glGetInteger64i(@NativeType(value="GLenum") int pname, @NativeType(value="GLuint") int index) {
        return GL32C.glGetInteger64i((int)pname, (int)index);
    }

    public static void nglGetSynciv(long sync, int pname, int bufSize, long length, long values) {
        GL32C.nglGetSynciv((long)sync, (int)pname, (int)bufSize, (long)length, (long)values);
    }

    public static void glGetSynciv(@NativeType(value="GLsync") long sync, @NativeType(value="GLenum") int pname, @NativeType(value="GLsizei *") IntBuffer length, @NativeType(value="GLint *") IntBuffer values) {
        GL32C.glGetSynciv((long)sync, (int)pname, (IntBuffer)length, (IntBuffer)values);
    }

    @NativeType(value="void")
    public static int glGetSynci(@NativeType(value="GLsync") long sync, @NativeType(value="GLenum") int pname, @NativeType(value="GLsizei *") IntBuffer length) {
        return GL32C.glGetSynci((long)sync, (int)pname, (IntBuffer)length);
    }

    public static void glGetBufferParameteri64v(@NativeType(value="GLenum") int target, @NativeType(value="GLenum") int pname, @NativeType(value="GLint64 *") long[] params) {
        GL32C.glGetBufferParameteri64v((int)target, (int)pname, (long[])params);
    }

    public static void glMultiDrawElementsBaseVertex(@NativeType(value="GLenum") int mode, @NativeType(value="GLsizei const *") int[] count, @NativeType(value="GLenum") int type, @NativeType(value="void const * const *") PointerBuffer indices, @NativeType(value="GLint *") int[] basevertex) {
        GL32C.glMultiDrawElementsBaseVertex((int)mode, (int[])count, (int)type, (PointerBuffer)indices, (int[])basevertex);
    }

    public static void glGetMultisamplefv(@NativeType(value="GLenum") int pname, @NativeType(value="GLuint") int index, @NativeType(value="GLfloat *") float[] val) {
        GL32C.glGetMultisamplefv((int)pname, (int)index, (float[])val);
    }

    public static void glGetInteger64v(@NativeType(value="GLenum") int pname, @NativeType(value="GLint64 *") long[] params) {
        GL32C.glGetInteger64v((int)pname, (long[])params);
    }

    public static void glGetInteger64i_v(@NativeType(value="GLenum") int pname, @NativeType(value="GLuint") int index, @NativeType(value="GLint64 *") long[] params) {
        GL32C.glGetInteger64i_v((int)pname, (int)index, (long[])params);
    }

    public static void glGetSynciv(@NativeType(value="GLsync") long sync, @NativeType(value="GLenum") int pname, @NativeType(value="GLsizei *") int[] length, @NativeType(value="GLint *") int[] values) {
        GL32C.glGetSynciv((long)sync, (int)pname, (int[])length, (int[])values);
    }

    static {
        GL.initialize();
    }
}
