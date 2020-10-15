package spi.test;

import org.apache.dubbo.common.extension.ExtensionLoader;
import spi.service.Robot;

/**
 * @author : niezl
 * @date : 2020/9/24
 */
public class DubboSpiTest {
    public static void main(String[] args) {
        ExtensionLoader<Robot> extensionLoader =
                ExtensionLoader.getExtensionLoader(Robot.class);
        Robot optimusPrime = extensionLoader.getExtension("optimusPrime");
        optimusPrime.sayHello();
        Robot bumblebee = extensionLoader.getExtension("bumblebee");
        bumblebee.sayHello();
    }
}
