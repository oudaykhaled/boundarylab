import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.*;
import java.util.*;
public class CapabilityProbe {
 public static void main(String[] args) throws Exception {
  Path dir=Files.createTempDirectory("bl-capability-");
  try {
   Path p=dir.resolve("abc.txt"); Files.writeString(p,"abc");
   try(DirectoryStream<Path> ds=Files.newDirectoryStream(dir)) {
    System.out.println("java="+System.getProperty("java.version"));
    System.out.println("os="+System.getProperty("os.name")+" "+System.getProperty("os.arch"));
    System.out.println("directoryStream="+ds.getClass().getName());
    System.out.println("secureDirectoryStream="+(ds instanceof SecureDirectoryStream));
    System.out.println("fileAttributeViews="+FileSystems.getDefault().supportedFileAttributeViews());
    if(ds instanceof SecureDirectoryStream<Path> secure) {
     try(SeekableByteChannel ch=secure.newByteChannel(Path.of("abc.txt"),Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))) {
      System.out.println("relativeNoFollowChannel="+ch.getClass().getName()+" size="+ch.size());
      System.out.println("channelPublicAttributeView=false; SeekableByteChannel/FileChannel provide no descriptor stat or unix:nlink API");
     }
    }
    System.out.println("pathNlink="+Files.getAttribute(p,"unix:nlink",LinkOption.NOFOLLOW_LINKS)+" (path-only, insufficient same-handle proof)");
   }
   Files.delete(p);
  } finally { Files.delete(dir); }
 }
}
