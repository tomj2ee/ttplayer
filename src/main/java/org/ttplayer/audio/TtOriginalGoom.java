package org.ttplayer.audio;

import java.util.Arrays;

/** Port of the C original GOOM implementation. */
final class TtOriginalGoom {
    private static final int[] POINT_COLORS = {0x003300ff, 0x000000ff, 0x00ff0055, 0, 0x00ff00ff};
    private static final int[] LINE_COLORS = {14453800, 15104000, 15507496, 16545792, 5294080, 5250810, 16};
    private static final class Line {
        final float[] currentX = new float[512];
        final float[] currentY = new float[512];
        final float[] currentAngle = new float[512];
        final float[] targetX = new float[512];
        final float[] targetY = new float[512];
        final float[] targetAngle = new float[512];
        int color;
        int targetColor;
        int mode;
        float parameter;
        float power;
        float powerStep;
    }

    private static final class Zoom {
        int speed;
        int centerX;
        int centerY;
        int horizontal;
        int vertical;
        int mode;
        int noise;
        boolean reverse;
        boolean wave;
        boolean hypercos;
    }

    private final TtLegacyIfs.RandomState random = new TtLegacyIfs.RandomState();
    private final Line[] lines = {new Line(), new Line()};
    private final Zoom zoom = new Zoom();
    private final int[] sine = new int[65536];
    private final int[] ifsVelocity = new int[4];
    private final int[] ifsColors = new int[4];
    private final float[] pointHorizontal = new float[5];
    private final float[] pointVertical = new float[5];
    private final float[] pointPeriodsX = new float[5];
    private final float[] pointPeriodsY = new float[5];
    private final long[] pointPhases = new long[5];
    private TtLegacyIfs ifs;
    private int[] front;
    private int[] back;
    private int[] sourceMap;
    private int[] targetMap;
    private int[] fire;
    private int width;
    private int height;
    private int ratio;
    private int wave;
    private int waveSpeed;
    private int speed;
    private int sensitivity;
    private int lock;
    private int goomAge;
    private int totalGooms;
    private int goomCount;
    private int ifsIncrement;
    private int ifsDecay;
    private int ifsRecovery;
    private int lineMode;
    private int sinceChange;
    private int previousZoomSpeed;
    private int switchIncrement;
    private float switchMultiplier;
    private int cycle;
    private int pointPhase;
    private int ifsColor;
    private int ifsMode;
    private int ifsHold;
    private int ifsCycle;

    TtOriginalGoom() {
        random.state = 1;
        for (int index = 0; index < 16383; index++) random.table[index] = (short) random.raw();
        random.cursor = 16383;
        sensitivity = 2;
        ifsIncrement = 1;
        zoom.speed = 127;
        zoom.centerX = 1;
        zoom.centerY = 1;
        switchIncrement = 255;
        switchMultiplier = 1f;
        previousZoomSpeed = 127;
        ifsColor = 0xc0c0c0c0;
        ifsMode = 2;
        int[] velocities = {2, 4, 3, 2};
        for (int index = 0; index < 4; index++) ifsVelocity[index] = ifsColors[index] = velocities[index];
        for (int index = 0; index < 65535; index++) {
            sine[index] = (int) (Math.sin(index * 0.00009587670522171699) * 1024.0 + 0.5);
        }
    }

    void render(int[] pixels, int outputWidth, int outputHeight, short[][] voice) {
        if (outputWidth < 4 || outputHeight < 4) {
            Arrays.fill(pixels, 0xff000000);
            return;
        }
        int sourceWidth = Math.min(outputWidth, 320);
        int sourceHeight = Math.min(outputHeight, 240);
        if (width != sourceWidth || height != sourceHeight) resize(sourceWidth, sourceHeight);
        updateFrame(voice);
        for (int row = 0; row < outputHeight; row++) {
            int sourceRow = row * sourceHeight / outputHeight * sourceWidth;
            for (int column = 0; column < outputWidth; column++) {
                int source = front[sourceRow + column * sourceWidth / outputWidth];
                pixels[row * outputWidth + column] = 0xff000000 | source & 0x00ffffff;
            }
        }
    }

    private void resize(int nextWidth, int nextHeight) {
        int count = nextWidth * nextHeight;
        int[] nextFront = new int[count];
        int[] nextBack = new int[count];
        int[] nextSource = new int[count * 2];
        int[] nextTarget = new int[count * 2];
        int[] nextFire = new int[nextHeight];
        TtLegacyIfs nextIfs = new TtLegacyIfs(random, nextWidth, nextHeight);
        boolean initial = front == null;
        front = nextFront;
        back = nextBack;
        sourceMap = nextSource;
        targetMap = nextTarget;
        fire = nextFire;
        ifs = nextIfs;
        width = nextWidth;
        height = nextHeight;
        ratio = 0;
        for (int row = 0; row < nextHeight; row++) {
            for (int column = 0; column < nextWidth; column++) {
                int offset = (row * nextWidth + column) * 2;
                sourceMap[offset] = targetMap[offset] = column * 16;
                sourceMap[offset + 1] = targetMap[offset + 1] = row * 16;
            }
        }
        for (int index = 0; index < 2; index++) {
            Line line = lines[index];
            if (initial) {
                generateLine(line.currentX, line.currentY, line.currentAngle, 1, index == 0 ? nextHeight : 0f);
                line.mode = 0;
                line.parameter = index == 0 ? 2f * nextHeight / 5f : nextHeight / 5f;
                line.color = lineColor(6);
                line.targetColor = lineColor(index == 0 ? 4 : 1);
                line.powerStep = 0.01f;
            }
            generateLine(line.targetX, line.targetY, line.targetAngle, line.mode, line.parameter);
        }
    }

    private void updateFrame(short[][] voice) {
        int peak = 0;
        for (int sample = 0; sample < 512; sample++) if (voice[0][sample] > peak) peak = voice[0][sample];
        int acceleration = peak / 5000;
        if (speed > 5) {
            acceleration--;
            if (speed > 20) acceleration--;
            if (speed > 40) speed = 40;
        }
        acceleration--;
        speed += acceleration;
        speed = Math.max(0, Math.min(40, speed));
        if (ifsIncrement == 1 && random.next() % 200 == 0 && ifsDecay < -300 && goomAge != 0) ifsDecay = 200;
        ifsDecay--;
        if (ifsDecay > 0) ifsIncrement += 2;
        if (ifsDecay == 0) ifsIncrement = 0;
        if (ifsIncrement == 0 && random.next() % 200 == 0 && goomAge != 0 && ifsDecay < -100) {
            ifsRecovery = 5;
            ifsIncrement = 11;
        }
        if (ifsRecovery != 0) {
            ifsIncrement -= 2;
            if (--ifsRecovery == 0) ifsIncrement = 1;
        }
        if (ifsIncrement > 0) drawIfs();
        drawPoints(peak);
        boolean changed = cycle == 0;
        if (lock > 0) lock--;
        if (goomAge > 0) goomAge--;
        boolean beat = acceleration > sensitivity || acceleration < -sensitivity;
        if (beat || sinceChange > 300) {
            totalGooms++;
            goomAge = 20;
            chooseFilter();
        }
        if (lock == 0) {
            if (beat) changed = changeZoom(128 - speed / 2, changed);
            if (random.next() % 700 == 0) {
                lock += 50;
                zoom.speed = 127;
                switchIncrement = 255;
                switchMultiplier = 1f;
                changed = true;
            }
        }
        if (speed < 1 && zoom.speed < 124 && cycle % 16 == 0) {
            zoom.speed += 3;
            goomCount = 0;
            changed = true;
        }
        if (cycle % 73 == 0 && zoom.speed < 123) {
            zoom.speed++;
            changed = true;
        }
        if (changed) {
            sinceChange = 0;
            int difference = Math.abs(zoom.speed - previousZoomSpeed);
            switchIncrement = difference > 2 ? 255 * ((difference + 2) / 2) : 255;
            switchMultiplier = 1f;
            previousZoomSpeed = zoom.speed;
            if (acceleration > sensitivity && totalGooms < 2) {
                switchIncrement = 0;
                switchMultiplier = 0.95f;
            }
        } else if (sinceChange <= 300) {
            sinceChange++;
        } else {
            sinceChange = 0;
            changed = true;
        }
        zoom(changed);
        if (lineMode != 70 && lineMode > 0) lineMode--;
        if (goomAge > 0 && totalGooms > 2 && cycle % 120 == 0 && random.next() % 3 == 0) {
            if (lineMode == 0) lineMode = 70;
            else if (lineMode == 70) {
                lineMode = 69;
                chooseLines(true);
            }
        }
        if (lineMode != 0 || goomAge > 15) {
            drawLines(voice);
            if (cycle % 101 == 9 && random.next() % 3 == 1 && (lineMode == 0 || lineMode == 70)) chooseLines(false);
        }
        int[] previous = front;
        front = back;
        back = previous;
        cycle++;
        if (cycle % 100 == 0) {
            if (totalGooms > 15 && sensitivity < 127) sensitivity++;
            else if (totalGooms == 0 && sensitivity > 1) sensitivity--;
            totalGooms = 0;
        }
    }

    private void chooseFilter() {
        switch (random.next() % 28) {
            case 0: case 10:
                zoom.hypercos = random.next() % 2 != 0;
                zoom.mode = 1;
                zoom.reverse = false;
                zoom.wave = random.next() % 3 == 0;
                if (random.next() % 2 != 0) zoom.speed = (zoom.speed + 127) / 2;
                break;
            case 13: case 20: case 21:
                zoom.mode = 1;
                zoom.reverse = false;
                zoom.wave = random.next() % 3 == 0;
                if (random.next() % 2 != 0) zoom.speed = (zoom.speed + 127) / 2;
                break;
            case 1: case 11: zoom.mode = 2; zoom.wave = zoom.hypercos = false; break;
            case 2: case 12:
                zoom.mode = 4;
                zoom.wave = random.next() % 3 == 0;
                if (random.next() % 3 == 0) zoom.hypercos = true;
                break;
            case 3: zoom.mode = 5; zoom.wave = zoom.hypercos = false; break;
            case 4: case 14: zoom.mode = 3; zoom.wave = zoom.hypercos = false; break;
            case 5: case 15:
                zoom.mode = 6;
                zoom.wave = false;
                if (random.next() % 3 == 0) zoom.hypercos = true;
                break;
            case 6: case 16: zoom.mode = 7; zoom.wave = zoom.hypercos = false; break;
            case 7: case 17:
                zoom.mode = 2;
                zoom.wave = random.next() % 4 == 0;
                zoom.hypercos = random.next() % 2 != 0;
                break;
            case 8: case 18: case 19: zoom.mode = 3; zoom.wave = zoom.hypercos = true; break;
            default: zoom.mode = 0; zoom.wave = zoom.hypercos = false; break;
        }
    }

    private boolean changeZoom(int targetSpeed, boolean changed) {
        goomCount++;
        lock = 50;
        if (zoom.reverse && cycle % 13 == 0 && random.raw() % 5 == 0) {
            zoom.reverse = false;
            zoom.speed = 126;
            lock = 75;
        }
        if (random.next() % 10 == 0) {
            zoom.reverse = true;
            lock = 100;
        }
        if (random.next() % 10 == 0) zoom.speed = 127;
        if (random.next() % 12 == 0) zoom.speed = 129;
        int center = random.next() % 25;
        if (center == 0 || center == 3 || center == 6) {
            zoom.centerX = width / 2;
            zoom.centerY = height - 1;
        } else if (center == 1 || center == 4) zoom.centerX = width - 1;
        else if (center == 2) zoom.centerX = 1;
        else {
            zoom.centerX = width / 2;
            zoom.centerY = height / 2;
        }
        int plane = random.next() % 15;
        if (plane == 8) {
            zoom.horizontal = random.next() % 8 + 5;
            zoom.vertical = -zoom.horizontal;
        } else if (plane == 9) {
            zoom.vertical = random.next() % 8 + 5;
            zoom.horizontal = -zoom.horizontal;
        } else {
            if (plane == 0 || plane == 3 || plane == 13 || plane == 14) {
                random.skip(plane == 0 || plane == 14 ? 4 : 2);
                zoom.horizontal = zoom.vertical = 0;
            } else if (plane >= 4 && plane <= 7) random.skip(2);
            if (plane < 10) zoom.horizontal = zoom.vertical = 0;
        }
        if (random.next() % 5 == 0) {
            zoom.noise = random.next() % 3 + 2;
            lock *= 2;
        } else zoom.noise = 0;
        if (center == 4) zoom.horizontal = zoom.vertical = zoom.noise = 0;
        if (zoom.centerX == 1 || zoom.centerX == width - 1) {
            zoom.vertical = 0;
            if (random.next() % 2 != 0) zoom.horizontal = 0;
        }
        if (targetSpeed < zoom.speed) {
            changed = true;
            if ((targetSpeed >= 121 || zoom.speed >= 122 || cycle % 3 != 0) && random.next() % 40 != 0) {
                zoom.speed = (targetSpeed + 4 * zoom.speed) / 5;
            } else {
                random.skip(2);
                zoom.speed = 128;
                zoom.reverse = !zoom.reverse;
            }
            lock += 50;
        }
        if (lock > 150) {
            switchIncrement = 255;
            switchMultiplier = 1f;
        }
        return changed;
    }

    private void drawPoints(int peak) {
        if (ifsIncrement == 1) return;
        float factor = (float) ((speed * 0.025 + peak * 0.00019999999) * 0.66666669);
        if (factor > 1.5f) factor = 1.5f;
        int spanX = width * 2 / 5;
        int spanY = height * 2 / 5;
        for (int particle = 1; particle * 15 <= speed + 15; particle++) {
            pointPhase += speed + 1;
            float scale = particle;
            pointHorizontal[0] = (spanX - 6f) * factor + 5f;
            pointHorizontal[1] = spanX / 2f * factor / scale + 10f * scale;
            pointHorizontal[2] = (spanY / 3f + 5f) * factor / scale + 10f * scale;
            pointHorizontal[3] = spanY / 3f * factor + 20f;
            pointHorizontal[4] = (spanY * factor + 10f * scale) / scale;
            pointVertical[0] = (spanY - 6f) * factor + 5f;
            pointVertical[1] = spanY / 2f * factor / scale + 10f * scale;
            pointVertical[2] = pointHorizontal[2];
            pointVertical[3] = pointHorizontal[3];
            pointVertical[4] = pointHorizontal[4];
            pointPeriodsX[0] = scale * 152f;
            pointPeriodsX[1] = 96f;
            pointPeriodsX[2] = scale + 122f;
            pointPeriodsX[3] = 58f;
            pointPeriodsX[4] = 66f;
            pointPeriodsY[0] = 128f;
            pointPeriodsY[1] = scale * 80f;
            pointPeriodsY[2] = 134f;
            pointPeriodsY[3] = scale * 66f;
            pointPeriodsY[4] = 74f;
            long unsignedPhase = Integer.toUnsignedLong(pointPhase);
            pointPhases[0] = Integer.toUnsignedLong(pointPhase + particle * 2032);
            pointPhases[1] = pointPhases[2] = pointPhases[3] = unsignedPhase / particle;
            pointPhases[4] = Integer.toUnsignedLong(pointPhase + particle * 500);
            for (int index = 0; index < 5; index++) {
                int column = zoom.centerX + (int) (pointHorizontal[index] * Math.cos(pointPhases[index] / pointPeriodsX[index]));
                int row = zoom.centerY + (int) (pointVertical[index] * Math.sin(pointPhases[index] / pointPeriodsY[index]));
                if (column > 1 && column < width - 2 && row > 1 && row < height - 2) {
                    int offset = row * width + column;
                    front[offset + 1] = POINT_COLORS[index];
                    front[offset + width] = POINT_COLORS[index];
                    front[offset + width + 1] = 0x00ffff00;
                    front[offset + width + 2] = POINT_COLORS[index];
                    front[offset + width * 2 + 1] = POINT_COLORS[index];
                }
            }
        }
    }

    private void zoom(boolean changed) {
        int count = width * height;
        if (changed) {
            for (int index = 0; index < count * 2; index++) {
                sourceMap[index] += wrappedProductShift(ratio, targetMap[index] - sourceMap[index], 16);
            }
            generateMap();
            ratio = 0;
        }
        ratio += switchIncrement;
        if (ratio > 65535) ratio = 65535;
        if (switchMultiplier != 1f) {
            ratio = (int) (ratio * switchMultiplier + (1.0 - switchMultiplier) * 65535.0);
        }
        for (int index = 0; index < count; index++) {
            int mappedX = sourceMap[index * 2]
                    + wrappedProductShift(ratio, targetMap[index * 2] - sourceMap[index * 2], 16);
            int mappedY = sourceMap[index * 2 + 1]
                    + wrappedProductShift(ratio, targetMap[index * 2 + 1] - sourceMap[index * 2 + 1], 16);
            if (mappedX < 0 || mappedX >= (width - 1) * 16 || mappedY < 0 || mappedY >= (height - 1) * 16) {
                mappedX = mappedY = 0;
            }
            int horizontal = mappedX & 15;
            int vertical = mappedY & 15;
            int firstWeight = (16 - horizontal) * (16 - vertical);
            int secondWeight = horizontal * (16 - vertical);
            int thirdWeight = (16 - horizontal) * vertical;
            int fourthWeight = horizontal * vertical;
            if (firstWeight != 0) firstWeight--;
            if (secondWeight != 0) secondWeight--;
            if (thirdWeight != 0) thirdWeight--;
            if (fourthWeight != 0) fourthWeight--;
            int source = mappedY / 16 * width + mappedX / 16;
            int first = front[source];
            int second = front[source + 1];
            int third = front[source + width];
            int fourth = front[source + width + 1];
            int result = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int channel = ((first >>> shift) & 255) * firstWeight
                        + ((second >>> shift) & 255) * secondWeight
                        + ((third >>> shift) & 255) * thirdWeight
                        + ((fourth >>> shift) & 255) * fourthWeight;
                result |= channel >> 8 << shift;
            }
            back[index] = result;
        }
    }

    private void generateMap() {
        int mappedSpeed = zoom.reverse ? 256 - zoom.speed : zoom.speed;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int mappedX;
                int mappedY;
                if (zoom.mode == 5) {
                    int sourceRow = row + wave / 10;
                    if (sourceRow < 0) sourceRow = 0;
                    if (sourceRow >= height) sourceRow = height - 1;
                    random.skip(4);
                    mappedX = column * 16 + fire[sourceRow] + wave / 10;
                    mappedY = row * 16 + 132 - (mappedSpeed >= 132 ? 131 : mappedSpeed);
                    if (wave < -10) waveSpeed += 2;
                    if (wave > 10) waveSpeed -= 2;
                    wave += waveSpeed / 10;
                    random.skip(2);
                    if (waveSpeed > 100) waveSpeed = waveSpeed * 9 / 10;
                } else {
                    if (zoom.noise != 0) random.skip(4);
                    int velocityX = (column - zoom.centerX) * 512 + zoom.horizontal * (row - zoom.centerY);
                    int velocityY = (row - zoom.centerY) * 512 + zoom.vertical * (column - zoom.centerX);
                    int scaledSpeed = mappedSpeed * 16;
                    if (zoom.wave) scaledSpeed = scaledSpeed * (1024 + sine[0] / 64) / 1024;
                    if (zoom.hypercos) {
                        velocityX += sine[-velocityY & 0xffff] / 2;
                        velocityY += sine[velocityX & 0xffff] / 2;
                    }
                    int normalizedX = velocityX / 512;
                    int normalizedY = velocityY / 512;
                    int distance = normalizedX * normalizedX + normalizedY * normalizedY;
                    switch (zoom.mode) {
                        case 1: scaledSpeed = scaledSpeed * (1024 + sine[-4 * distance & 0xffff] / 64) / 1024; break;
                        case 2: scaledSpeed += distance * 4 / 1024; break;
                        case 3: scaledSpeed -= distance * 4 / 512; break;
                        case 4: scaledSpeed -= distance * 4 / 16; break;
                        case 6:
                            velocityX += sine[distance - velocityY & 0xffff] / 2;
                            velocityY += sine[velocityX + distance & 0xffff] / 2;
                            break;
                        case 7:
                            velocityX += sine[distance - velocityY / 2 & 0xffff];
                            velocityY += sine[distance + velocityX / 2 & 0xffff];
                            scaledSpeed = 2048;
                            break;
                        default:
                            break;
                    }
                    if (scaledSpeed < -3024) scaledSpeed = -3024;
                    mappedX = zoom.centerX * 16 + (velocityX < 0
                            ? -wrappedProductShift(-velocityX, scaledSpeed, 16)
                            : wrappedProductShift(velocityX, scaledSpeed, 16));
                    mappedY = zoom.centerY * 16 + (velocityY < 0
                            ? -wrappedProductShift(-velocityY, scaledSpeed, 16)
                            : wrappedProductShift(velocityY, scaledSpeed, 16));
                }
                if (mappedX == column * 16 && mappedY == row * 16) {
                    mappedY += column <= zoom.centerX ? -2 : 2;
                    mappedX += row <= zoom.centerY ? -2 : 2;
                }
                int offset = (row * width + column) * 2;
                targetMap[offset] = mappedX;
                targetMap[offset + 1] = mappedY;
            }
        }
    }

    private static int wrappedProductShift(int first, int second, int shift) {
        int product = first * second;
        long divisor = 1L << shift;
        return product >= 0 ? (int) (product / divisor)
                : (int) -((-(long) product + divisor - 1) / divisor);
    }

    private void chooseLines(boolean black) {
        int mode = random.next() % 3;
        float first;
        float second;
        if (mode == 0) {
            if (random.next() % 3 == 0) first = second = 0f;
            else if (random.next() % 2 != 0) {
                first = height * 0.4f;
                second = height * 0.2f;
            } else first = second = height * 0.25f;
        } else {
            float span = mode == 1 ? height : width;
            if (random.next() % (mode == 1 ? 4 : 3) == 0) first = second = span * 0.5f;
            else {
                first = span / 7f;
                second = span * (6f / 7f);
            }
        }
        int selectedColor = random.next() % 6;
        for (int index = 0; index < 2; index++) {
            Line line = lines[index];
            line.mode = mode;
            line.parameter = index == 0 ? first : second;
            line.targetColor = lineColor(black ? 6 : index == 0 ? selectedColor : 5 - selectedColor);
            generateLine(line.targetX, line.targetY, line.targetAngle, mode, line.parameter);
        }
    }

    private void generateLine(float[] x, float[] y, float[] angle, int mode, float parameter) {
        for (int sample = 0; sample < 512; sample++) {
            if (mode == 1) {
                x[sample] = sample * width / 512f;
                y[sample] = parameter;
                angle[sample] = (float) (Math.PI / 2.0);
            } else if (mode == 2) {
                x[sample] = parameter;
                y[sample] = sample * height / 512f;
                angle[sample] = 0f;
            } else {
                angle[sample] = sample * 0.012271847f;
                x[sample] = width * 0.5f + parameter * (float) Math.cos(angle[sample]);
                y[sample] = height * 0.5f + parameter * (float) Math.sin(angle[sample]);
            }
        }
    }

    private void drawLines(short[][] voice) {
        lines[1].power = lines[0].power;
        for (int index = 0; index < 2; index++) {
            Line line = lines[index];
            int color = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                double light = Math.exp(((line.color >>> shift) & 255) / 32.0) + line.power;
                int value = light > 0 ? (int) (Math.log(light) * 32.0) : 0;
                value = Math.max(0, Math.min(255, value));
                color |= value << shift;
            }
            int previousX = 0;
            int previousY = 0;
            for (int sample = 0; sample < 512; sample++) {
                float horizontal = (float) (Math.cos(line.currentAngle[sample]) * 0.001);
                float vertical = (float) (Math.sin(line.currentAngle[sample]) * 0.001);
                int column = (int) (line.currentX[sample] + horizontal * voice[index][sample]);
                int row = (int) (line.currentY[sample] + vertical * voice[index][sample]);
                if (sample != 0) segment(previousX, previousY, column, row, color);
                previousX = column;
                previousY = row;
                line.currentX[sample] = (float) ((line.currentX[sample] * 39.0 + line.targetX[sample]) * 0.025);
                line.currentY[sample] = (float) ((line.currentY[sample] * 39.0 + line.targetY[sample]) * 0.025);
                line.currentAngle[sample] = (float) ((line.currentAngle[sample] * 39.0 + line.targetAngle[sample]) * 0.025);
            }
            int nextColor = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                nextColor |= ((((line.color >>> shift) & 255) * 63 + ((line.targetColor >>> shift) & 255)) >> 6) << shift;
            }
            line.color = nextColor;
            line.power += line.powerStep;
            if (line.power < -2.6f) {
                line.power = -2.6f;
                line.powerStep = (random.next() % 20 + 10) / 600f;
            }
            if (line.power > 0.6f) {
                line.power = 0.6f;
                line.powerStep = -(random.next() % 20 + 10) / 600f;
            }
        }
    }

    private void segment(int startX, int startY, int endX, int endY, int color) {
        if (startY < 0) {
            if (endY < 0) return;
            startX += startY * (startX - endX) / (endY - startY);
            startY = 0;
        }
        if (endY < 0) {
            endX += endY * (startX - endX) / (endY - startY);
            endY = 0;
        }
        if (startY >= height) {
            if (endY >= height) return;
            startX -= (height - startY) * (startX - endX) / (endY - startY);
            startY = height - 1;
        }
        if (endY >= height) {
            endX -= (height - endY) * (startX - endX) / (endY - startY);
            endY = height - 1;
        }
        if (startX < 0) {
            if (endX < 0) return;
            startY += startX * (startY - endY) / (endX - startX);
            startX = 0;
        }
        if (endX < 0) {
            endY += endX * (startY - endY) / (endX - startX);
            endX = 0;
        }
        if (startX >= width) {
            if (endX >= width) return;
            startY -= (startY - endY) * (width - startX) / (endX - startX);
            startX = width - 1;
        }
        if (endX >= width) {
            endY -= (width - endX) * (startY - endY) / (endX - startX);
            endX = width - 1;
        }
        if (startX > endX) {
            int temporary = startX;
            startX = endX;
            endX = temporary;
            temporary = startY;
            startY = endY;
            endY = temporary;
        }
        int spanX = endX - startX;
        int spanY = endY - startY;
        if (spanX == 0) {
            int first = Math.min(startY, endY);
            int last = Math.max(startY, endY);
            for (int row = first; row <= last; row++) addPixel(startX, row, color);
        } else if (Math.abs(spanY) <= spanX) {
            int position = startY * 65536;
            int increment = spanY * 65536 / spanX;
            for (int column = startX; column <= endX; column++) {
                addPixel(column, position / 65536, color);
                position += increment;
            }
        } else {
            int position = (startX + (spanY < 0 ? 1 : 0)) * 65536;
            int direction = spanY < 0 ? -1 : 1;
            int increment = spanX * 65536 / spanY;
            for (int row = startY; direction > 0 ? row <= endY : row >= endY; row += direction) {
                addPixel(position / 65536, row, color);
                position += direction * increment;
            }
        }
    }

    private void addPixel(int column, int row, int color) {
        if (column >= 0 && column < width && row >= 0 && row < height) {
            int offset = row * width + column;
            back[offset] = add(back[offset], color);
        }
    }

    private static int lineColor(int color) {
        return color >= 0 && color < LINE_COLORS.length ? LINE_COLORS[color] : 0;
    }

    private static int add(int first, int second) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int sum = ((first >>> shift) & 255) + ((second >>> shift) & 255);
            result |= Math.min(sum, 255) << shift;
        }
        return result;
    }

    private void drawIfs() {
        ifsCycle = (ifsCycle + 1) % 80;
        int shade = ifsCycle < 40 ? ifsCycle / 10 : 7 - ifsCycle / 10;
        int color = 0;
        for (int channel = 0; channel < 4; channel++) {
            color |= ((ifsColor >>> channel * 8 & 255) >>> shade) << channel * 8;
        }
        TtLegacyIfs.Points points = ifs.points();
        for (int index = 0; index < points.count - 1; index += ifsIncrement) {
            int column = points.x[index];
            int row = points.y[index];
            if (column > 0 && column < width && row > 0 && row < height) {
                int offset = row * width + column;
                front[offset] = add(back[offset], color);
            }
        }
        ifsHold--;
        ifsColors[3] = ifsColor >>> 24 & 255;
        ifsColors[0] = ifsColor & 255;
        ifsColors[1] = ifsColor >>> 8 & 255;
        ifsColors[2] = ifsColor >>> 16 & 255;

        if (ifsMode == 0) updateIfsModeZero();
        else if (ifsMode == 2) updateIfsModeTwo();
        else if (ifsMode == 1) updateIfsModeOne();

        ifsColor = ifsColors[3] << 24 | ifsColors[0] | ifsColors[1] << 8 | ifsColors[2] << 16;
    }

    private void updateIfsModeZero() {
        ifsColors[0] += ifsVelocity[0];
        if (ifsColors[0] > 255) {
            ifsColors[0] = 255;
            ifsVelocity[0] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[0] < 32) {
            ifsColors[0] = 32;
            ifsVelocity[0] = random.raw() % 4 + 1;
        }
        ifsColors[1] += ifsVelocity[1];
        if (ifsColors[1] > 200) {
            ifsColors[1] = 200;
            ifsVelocity[1] = -(random.raw() % 3) - 2;
        }
        if (ifsColors[1] > ifsColors[0]) {
            ifsColors[1] = ifsColors[0];
            ifsVelocity[1] = ifsVelocity[0];
        }
        if (ifsColors[1] < 32) {
            ifsColors[1] = 32;
            ifsVelocity[1] = random.raw() % 3 + 2;
        }
        ifsColors[2] += ifsVelocity[2];
        if (ifsColors[2] > 64) {
            ifsColors[2] = 64;
            ifsVelocity[2] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[2] < 0) {
            ifsColors[2] = 0;
            ifsVelocity[2] = random.raw() % 4 + 1;
        }
        ifsColors[3] += ifsVelocity[3];
        if (ifsColors[3] > 0) {
            ifsColors[3] = 0;
            ifsVelocity[3] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[3] < 0) {
            ifsColors[3] = 0;
            ifsVelocity[3] = random.raw() % 4 + 1;
        }
        if (ifsColors[1] > 32 && ifsColors[2] < ifsColors[1] + 40 && ifsColors[1] < ifsColors[2] + 20
                && ifsColors[0] < 64 && random.raw() % 20 == 0 && ifsHold < 0) {
            ifsMode = random.raw() % 3 != 0 ? 1 : 2;
            ifsHold = 250;
        }
    }

    private void updateIfsModeTwo() {
        ifsColors[0] += ifsVelocity[0];
        if (ifsColors[0] > 128) {
            ifsColors[0] = 128;
            ifsVelocity[0] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[0] < 16) {
            ifsColors[0] = 16;
            ifsVelocity[0] = random.raw() % 4 + 1;
        }
        ifsColors[1] += ifsVelocity[1];
        if (ifsColors[1] > 200) {
            ifsColors[1] = 200;
            ifsVelocity[1] = -(random.raw() % 3) - 2;
        }
        if (ifsColors[1] > ifsColors[3]) {
            ifsColors[1] = ifsColors[3];
            ifsVelocity[1] = ifsVelocity[3];
        }
        if (ifsColors[1] < 32) {
            ifsColors[1] = 32;
            ifsVelocity[1] = random.raw() % 3 + 2;
        }
        ifsColors[2] += ifsVelocity[2];
        if (ifsColors[2] > 128) {
            ifsColors[2] = 128;
            ifsVelocity[2] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[2] < 0) {
            ifsColors[2] = 0;
            ifsVelocity[2] = random.raw() % 4 + 1;
        }
        ifsColors[3] += ifsVelocity[3];
        if (ifsColors[3] > 255) {
            ifsColors[3] = 255;
            ifsVelocity[3] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[3] < 0) {
            ifsColors[3] = 0;
            ifsVelocity[3] = random.raw() % 4 + 1;
        }
        if (ifsColors[1] > 32 && ifsColors[2] < ifsColors[1] + 40 && ifsColors[1] < ifsColors[2] + 20
                && ifsColors[0] < 64 && random.raw() % 20 == 0 && ifsHold < 0) {
            ifsMode = random.raw() % 3 != 0 ? 1 : 0;
            ifsHold = 250;
        }
    }

    private void updateIfsModeOne() {
        ifsColors[0] += ifsVelocity[0];
        if (ifsColors[0] > 64) {
            ifsColors[0] = 64;
            ifsVelocity[0] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[0] < 0) {
            ifsColors[0] = 0;
            ifsVelocity[0] = random.raw() % 4 + 1;
        }
        ifsColors[1] += ifsVelocity[1];
        if (ifsColors[1] > 200) {
            ifsColors[1] = 200;
            ifsVelocity[1] = -(random.raw() % 3) - 2;
        }
        if (ifsColors[1] > ifsColors[2] + 20) {
            ifsColors[1] = ifsColors[2] + 20;
            ifsVelocity[1] = -(random.raw() % 3) - 2;
            ifsVelocity[2] = random.raw() % 4 + 1;
            ifsVelocity[0] = random.raw() % 4 + 1;
        }
        if (ifsColors[1] < 0) {
            ifsColors[1] = 0;
            ifsVelocity[1] = random.raw() % 3 + 2;
        }
        ifsColors[2] += ifsVelocity[2];
        if (ifsColors[2] > 255) {
            ifsColors[2] = 255;
            ifsVelocity[2] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[2] > ifsColors[1] + 40) {
            ifsColors[2] = ifsColors[1] + 40;
            ifsVelocity[2] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[2] < 0) {
            ifsColors[2] = 0;
            ifsVelocity[2] = random.raw() % 4 + 1;
        }
        ifsColors[3] += ifsVelocity[3];
        if (ifsColors[3] > 0) {
            ifsColors[3] = 0;
            ifsVelocity[3] = -(random.raw() % 4) - 1;
        }
        if (ifsColors[3] < 0) {
            ifsColors[3] = 0;
            ifsVelocity[3] = random.raw() % 4 + 1;
        }
        if (ifsColors[2] < 64 && ifsColors[1] > 32 && ifsColors[1] < ifsColors[0] && ifsColors[0] > 32
                && random.raw() % 20 == 0 && ifsHold < 0) {
            ifsMode = random.raw() % 2 != 0 ? 0 : 2;
            ifsHold = 250;
        }
    }
}
