package ork.sevenstates.apng;

import ork.sevenstates.apng.filter.Filter;
import ork.sevenstates.apng.filter.None;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ScreenshotsMain {

    final File fileName;
    final Robot robot;
    final Dimension screenSize;

    ScreenshotsMain(File fileName,Robot robot,Dimension screenSize) {
        this.fileName = fileName;
        this.robot = robot;
        this.screenSize = screenSize;
    }

    public static void main(String[] args) throws Exception {
        File fileName = new File("screenshot.png");

        new ScreenshotsMain(fileName,new Robot(),screenSize()).writeScreenshots();
    }

    private APNGSeqWriter newWriter() throws IOException {
        int bands = screenshot().getRaster().getNumBands();
        Filter filter = new None(screenSize.width,screenSize.height,bands);
        return new APNGSeqWriter(fileName, filter);
    }

    private void writeImage(APNGSeqWriter writer) throws IOException {
        writer.writeImage(screenshot());
    }

    private BufferedImage screenshot() {
        return robot.createScreenCapture(new Rectangle(screenSize));
    }

    private static Dimension screenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    private void writeScreenshots() throws IOException {
        APNGSeqWriter writer = newWriter();
        for (int i = 0; i < 10; i++) {
            writeImage(writer);
            System.out.println(i);
            sleep();
        }
        writer.close();
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            System.out.println("???");
        }
    }
}
