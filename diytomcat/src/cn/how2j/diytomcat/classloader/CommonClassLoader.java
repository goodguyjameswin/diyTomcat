package cn.how2j.diytomcat.classloader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class CommonClassLoader extends URLClassLoader {

    public CommonClassLoader() {
        super(new URL[] {});
        try {
            File workingFolder = new File(System.getProperty("user.dir"));
            File libFolder = new File(workingFolder, "lib");
            File[] jarFiles = libFolder.listFiles();
            assert jarFiles != null;
            for (File file : jarFiles) {
                if (file.getName().endsWith("jar")) {
                    URL url = new URL("file:" + file.getAbsolutePath());
                    this.addURL(url); // 加到当前的库里面去，当调用其loadClass方法时，就会从这些jar里面去找了
                }
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
