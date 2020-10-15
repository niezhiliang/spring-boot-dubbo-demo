package spi.test;

import spi.service.Robot;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author : niezl
 * @date : 2020/9/24
 * Java SPI Demo
 */
public class JavaSpiTest {
    public static void main(String[] args) {
        ServiceLoader<Robot> serviceLoader = ServiceLoader.load(Robot.class);
        Iterator<Robot> iterator = serviceLoader.iterator();
        while (iterator.hasNext()) {
          Robot robot = iterator.next();
          robot.sayHello();
        }
    }
}
