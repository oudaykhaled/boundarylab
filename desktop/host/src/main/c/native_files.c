#define _POSIX_C_SOURCE 200809L
#define _DARWIN_C_SOURCE
#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#define MAX_BYTES 134217728LL
#define MAX_CHUNK 65536
#define JNI_FN(name) Java_dev_boundarylab_desktop_NativeFiles_##name

static void fail(JNIEnv *env, int error) {
    char message[40];
    snprintf(message, sizeof(message), "POSIX errno=%d", error);
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) (*env)->ThrowNew(env, cls, message);
}
static int regular_fd(int fd, struct stat *st) {
    if (fstat(fd, st) != 0) return errno;
    if (!S_ISREG(st->st_mode) || st->st_nlink != 1 || st->st_size < 0 || st->st_size > MAX_BYTES) return ENOTSUP;
    int flags = fcntl(fd, F_GETFL);
    if (flags < 0) return errno;
    if ((flags & O_ACCMODE) != O_RDONLY) return ENOTSUP;
    if (lseek(fd, 0, SEEK_CUR) < 0) return errno;
    return 0;
}
static int valid_alias(const char *alias) {
    size_t n = strlen(alias);
    if (n < 1 || n > 64 || alias[n-1] == '.') return 0;
    for (size_t i=0;i<n;i++) {
        unsigned char c = (unsigned char) alias[i];
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (i > 0 && (c == '.' || c == '_' || c == '-')))) return 0;
    }
    char base[65]; size_t count = strcspn(alias, ".");
    memcpy(base, alias, count); base[count] = '\0';
    if (!strcmp(base,"con") || !strcmp(base,"prn") || !strcmp(base,"aux") || !strcmp(base,"nul")) return 0;
    if (count == 4 && (!strncmp(base,"com",3) || !strncmp(base,"lpt",3)) && base[3] >= '1' && base[3] <= '9') return 0;
    return 1;
}
JNIEXPORT jint JNICALL JNI_FN(openRoot)(JNIEnv *env, jclass cls, jstring root) {
    (void)cls;
    if (root == NULL || (*env)->GetStringUTFLength(env, root) > 4096) { fail(env, EINVAL); return -1; }
    const char *path = (*env)->GetStringUTFChars(env, root, NULL);
    if (path == NULL) return -1;
    int fd = open(path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    int error = errno;
    (*env)->ReleaseStringUTFChars(env, root, path);
    if (fd < 0) { fail(env, error); return -1; }
    struct stat st;
    if (fstat(fd, &st) != 0 || !S_ISDIR(st.st_mode)) { error = errno ? errno : ENOTDIR; close(fd); fail(env,error); return -1; }
    return fd;
}
JNIEXPORT jint JNICALL JNI_FN(openAlias)(JNIEnv *env, jclass cls, jint root, jstring alias) {
    (void)cls;
    if (root < 0 || alias == NULL || (*env)->GetStringUTFLength(env, alias) > 64) { fail(env,EINVAL); return -1; }
    const char *name = (*env)->GetStringUTFChars(env, alias, NULL);
    if (name == NULL) return -1;
    if (!valid_alias(name)) { (*env)->ReleaseStringUTFChars(env,alias,name); fail(env,EINVAL); return -1; }
    int fd = openat(root, name, O_RDONLY | O_NOFOLLOW | O_NONBLOCK | O_CLOEXEC);
    int error = errno;
    (*env)->ReleaseStringUTFChars(env,alias,name);
    if (fd < 0) { fail(env,error); return -1; }
    struct stat st; error = regular_fd(fd,&st);
    if (error) { close(fd); fail(env,error); return -1; }
    return fd;
}
JNIEXPORT jlongArray JNICALL JNI_FN(snapshot)(JNIEnv *env, jclass cls, jint fd) {
    (void)cls;
    struct stat st;
    int error = 0;
    if (fstat(fd, &st) != 0) error = errno;
    else if (!S_ISDIR(st.st_mode)) error = regular_fd(fd, &st);
    if (error) { fail(env,error); return NULL; }
#ifdef __APPLE__
    jlong values[9] = {st.st_dev,st.st_ino,st.st_nlink,st.st_mode,st.st_size,st.st_mtimespec.tv_sec,st.st_mtimespec.tv_nsec,st.st_ctimespec.tv_sec,st.st_ctimespec.tv_nsec};
#else
    jlong values[9] = {st.st_dev,st.st_ino,st.st_nlink,st.st_mode,st.st_size,st.st_mtim.tv_sec,st.st_mtim.tv_nsec,st.st_ctim.tv_sec,st.st_ctim.tv_nsec};
#endif
    jlongArray result = (*env)->NewLongArray(env,9);
    if (result != NULL) (*env)->SetLongArrayRegion(env,result,0,9,values);
    return result;
}
JNIEXPORT jint JNICALL JNI_FN(pread)(JNIEnv *env, jclass cls, jint fd, jbyteArray buffer, jint start, jint length, jlong offset) {
    (void)cls;
    if (fd < 0 || buffer == NULL || start < 0 || length < 1 || length > MAX_CHUNK || offset < 0 || offset > MAX_BYTES) { fail(env,EINVAL); return -1; }
    jsize capacity = (*env)->GetArrayLength(env,buffer);
    if (start > capacity || length > capacity-start) { fail(env,EINVAL); return -1; }
    /* Bounded stack buffer; no Java array is held across potentially blocking IO. */
    unsigned char bytes[MAX_CHUNK];
    ssize_t read_count;
    do { read_count = pread(fd,bytes,(size_t)length,(off_t)offset); } while (read_count < 0 && errno == EINTR);
    if (read_count < 0) { fail(env,errno); return -1; }
    if (read_count == 0) return -1;
    (*env)->SetByteArrayRegion(env,buffer,start,(jsize)read_count,(const jbyte*)bytes);
    return (jint)read_count;
}
JNIEXPORT jboolean JNICALL JNI_FN(revalidate)(JNIEnv *env, jclass cls, jint root, jstring alias, jint fd) {
    (void)cls;
    if (root < 0 || fd < 0 || alias == NULL || (*env)->GetStringUTFLength(env,alias)>64) { fail(env,EINVAL); return JNI_FALSE; }
    const char *name = (*env)->GetStringUTFChars(env,alias,NULL);
    if (name == NULL) return JNI_FALSE;
    if (!valid_alias(name)) { (*env)->ReleaseStringUTFChars(env,alias,name); fail(env,EINVAL); return JNI_FALSE; }
    struct stat opened, named;
    int error = regular_fd(fd,&opened);
    int result = fstatat(root,name,&named,AT_SYMLINK_NOFOLLOW);
    (*env)->ReleaseStringUTFChars(env,alias,name);
    if (error) { fail(env,error); return JNI_FALSE; }
    if (result != 0) return JNI_FALSE;
    return S_ISREG(named.st_mode) && named.st_nlink==1 && opened.st_dev==named.st_dev && opened.st_ino==named.st_ino ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL JNI_FN(close)(JNIEnv *env, jclass cls, jint fd) {
    (void)cls;
    if (fd < 0) { fail(env,EINVAL); return; }
    /* Never retry close: retry can close an unrelated reused descriptor. */
    if (close(fd) != 0) fail(env,errno);
}
