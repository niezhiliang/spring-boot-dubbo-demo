package spi.service;

import spi.service.Robot;

/**
 * @author : niezl
 * @date : 2020/9/24
 */
public class Bumblebee implements Robot {

    @Override
    public void sayHello() {
        System.out.println("Hello, I am Bumblebee.");
    }
}