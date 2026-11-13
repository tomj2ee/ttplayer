package org.ttplayer.audio;

/** Java port of the legacy IFS generator used by the C original GOOM effect. */
final class TtLegacyIfs {
    static final class RandomState {
        int state;
        int cursor;
        final short[] table = new short[16384];

        int raw() {
            state = state * 214013 + 2531011;
            return state >>> 16 & 32767;
        }

        int next() {
            int index = cursor++ & 16383;
            cursor &= 0xffff;
            return table[index];
        }

        void skip(int amount) {
            cursor = cursor + amount & 0xffff;
        }
    }

    static final class Points {
        int[] x;
        int[] y;
        int count;
    }

    private static final int FIX = 12;
    private static final int UNIT = 1 << FIX;
    private static final int MAX_SIMI = 6;

    private static final class Simi {
        float centerX;
        float centerY;
        float radius;
        float radius2;
        float angle;
        float angle2;
        int cosine;
        int sine;
        int cosine2;
        int sine2;
        int fixedX;
        int fixedY;
        int fixedRadius;
        int fixedRadius2;

        void copyFrom(Simi other) {
            centerX = other.centerX;
            centerY = other.centerY;
            radius = other.radius;
            radius2 = other.radius2;
            angle = other.angle;
            angle2 = other.angle2;
            cosine = other.cosine;
            sine = other.sine;
            cosine2 = other.cosine2;
            sine2 = other.sine2;
            fixedX = other.fixedX;
            fixedY = other.fixedY;
            fixedRadius = other.fixedRadius;
            fixedRadius2 = other.fixedRadius2;
        }
    }

    private static final class Fractal {
        int similitudeCount;
        final Simi[] components = new Simi[5 * MAX_SIMI];
        int depth;
        int color;
        int count;
        int speed;
        int width;
        int height;
        int halfWidth;
        int halfHeight;
        float radiusMean;
        float radiusDelta;
        float radius2Delta;
        int currentPoints;
        int maxPoints;
        int[] buffer1X;
        int[] buffer1Y;
        int[] buffer2X;
        int[] buffer2Y;

        Fractal() {
            for (int index = 0; index < components.length; index++) components[index] = new Simi();
        }
    }

    private final RandomState random;
    private final Fractal root = new Fractal();
    private final Points points = new Points();
    private int[] writeX;
    private int[] writeY;
    private int writeIndex;
    private int pointCount;

    TtLegacyIfs(RandomState random, int width, int height) {
        this.random = random;
        initialize(width, height);
    }

    Points points() {
        draw();
        points.x = root.buffer2X;
        points.y = root.buffer2Y;
        points.count = pointCount;
        return points;
    }

    private void initialize(int width, int height) {
        int count = random.raw() % 4 + 2;
        switch (count) {
            case 3:
                root.depth = 6;
                root.radiusMean = 0.6f;
                root.radiusDelta = 0.4f;
                root.radius2Delta = 0.3f;
                break;
            case 4:
                root.depth = 4;
                root.radiusMean = 0.5f;
                root.radiusDelta = 0.4f;
                root.radius2Delta = 0.3f;
                break;
            case 5:
                root.depth = 2;
                root.radiusMean = 0.5f;
                root.radiusDelta = 0.4f;
                root.radius2Delta = 0.3f;
                break;
            default:
                root.depth = 10;
                root.radiusMean = 0.7f;
                root.radiusDelta = 0.3f;
                root.radius2Delta = 0.4f;
                break;
        }
        root.similitudeCount = count;
        root.maxPoints = count - 1;
        for (int index = 0; index <= root.depth + 2; index++) root.maxPoints *= count;
        root.buffer1X = new int[root.maxPoints];
        root.buffer1Y = new int[root.maxPoints];
        root.buffer2X = new int[root.maxPoints];
        root.buffer2Y = new int[root.maxPoints];
        root.speed = 6;
        root.width = width;
        root.height = height;
        root.currentPoints = 0;
        root.count = 0;
        root.halfWidth = (width - 1) / 2;
        root.halfHeight = (height - 1) / 2;
        root.color = random.raw() % (width * height);
        randomSimis(root.components, 0, 5 * MAX_SIMI);
    }

    private void randomSimis(Simi[] values, int offset, int count) {
        for (int index = 0; index < count; index++) {
            Simi current = values[offset + index];
            current.centerX = gauss(0f, 0.8f, 4f);
            current.centerY = gauss(0f, 0.8f, 4f);
            current.radius = gauss(root.radiusMean, root.radiusDelta, 3f);
            current.radius2 = halfGauss(0f, root.radius2Delta, 2f);
            current.angle = gauss(0f, 360f, 4f) * ((float) Math.PI / 180f);
            current.angle2 = gauss(0f, 360f, 4f) * ((float) Math.PI / 180f);
        }
    }

    private float gauss(float center, float amplitude, float shape) {
        float value = random.raw() / 32767f;
        value = amplitude * (1f - (float) Math.exp(-value * value * shape)) / (1f - (float) Math.exp(-shape));
        return random.raw() % 2 != 0 ? center + value : center - value;
    }

    private float halfGauss(float center, float amplitude, float shape) {
        float value = random.raw() / 32767f;
        value = amplitude * (1f - (float) Math.exp(-value * value * shape)) / (1f - (float) Math.exp(-shape));
        return center + value;
    }

    private void draw() {
        float u = root.count * root.speed / 1000f;
        float uu = u * u;
        float v = 1f - u;
        float vv = v * v;
        float u0 = vv * v;
        float u1 = 3f * vv * u;
        float u2 = 3f * v * uu;
        float u3 = u * uu;
        int n = root.similitudeCount;
        for (int index = 0; index < n; index++) {
            Simi target = root.components[index];
            Simi first = root.components[n + index];
            Simi second = root.components[n * 2 + index];
            Simi third = root.components[n * 3 + index];
            Simi fourth = root.components[n * 4 + index];
            target.centerX = u0 * first.centerX + u1 * second.centerX + u2 * third.centerX + u3 * fourth.centerX;
            target.centerY = u0 * first.centerY + u1 * second.centerY + u2 * third.centerY + u3 * fourth.centerY;
            target.radius = u0 * first.radius + u1 * second.radius + u2 * third.radius + u3 * fourth.radius;
            target.radius2 = u0 * first.radius2 + u1 * second.radius2 + u2 * third.radius2 + u3 * fourth.radius2;
            target.angle = u0 * first.angle + u1 * second.angle + u2 * third.angle + u3 * fourth.angle;
            target.angle2 = u0 * first.angle2 + u1 * second.angle2 + u2 * third.angle2 + u3 * fourth.angle2;
        }
        drawFractal();
        if (root.count >= 1000 / root.speed) {
            for (int index = 0; index < n; index++) {
                Simi first = root.components[n + index];
                Simi second = root.components[n * 2 + index];
                Simi third = root.components[n * 3 + index];
                Simi fourth = root.components[n * 4 + index];
                second.centerX = 2f * fourth.centerX - third.centerX;
                second.centerY = 2f * fourth.centerY - third.centerY;
                second.radius = 2f * fourth.radius - third.radius;
                second.radius2 = 2f * fourth.radius2 - third.radius2;
                second.angle = 2f * fourth.angle - third.angle;
                second.angle2 = 2f * fourth.angle2 - third.angle2;
                first.copyFrom(fourth);
            }
            randomSimis(root.components, n * 3, n);
            randomSimis(root.components, n * 4, n);
            root.count = 0;
        } else {
            root.count++;
        }
        root.color++;
    }

    private void drawFractal() {
        for (int index = 0; index < root.similitudeCount; index++) prepare(root.components[index]);
        pointCount = 0;
        writeIndex = 0;
        writeX = root.buffer2X;
        writeY = root.buffer2Y;
        for (int outer = 0; outer < root.similitudeCount; outer++) {
            Simi current = root.components[outer];
            int originX = current.fixedX;
            int originY = current.fixedY;
            for (int inner = 0; inner < root.similitudeCount; inner++) {
                Simi simi = root.components[inner];
                if (simi == current) continue;
                long transformed = transform(simi, originX, originY);
                trace((int) (transformed >> 32), (int) transformed);
            }
        }
        root.currentPoints = pointCount;
        int[] swapX = root.buffer1X;
        int[] swapY = root.buffer1Y;
        root.buffer1X = root.buffer2X;
        root.buffer1Y = root.buffer2Y;
        root.buffer2X = swapX;
        root.buffer2Y = swapY;
    }

    private void trace(int originX, int originY) {
        for (int index = 0; index < root.similitudeCount; index++) {
            long transformed = transform(root.components[index], originX, originY);
            int x = (int) (transformed >> 32);
            int y = (int) transformed;
            if (pointCount >= root.maxPoints) return;
            writeX[writeIndex] = root.halfWidth + (x * root.halfWidth >> (FIX + 1));
            writeY[writeIndex] = root.halfHeight - (y * root.halfHeight >> (FIX + 1));
            writeIndex++;
            pointCount++;
            if (root.depth != 0 && ((x - originX) & ~15) != 0 && ((y - originY) & ~15) != 0) {
                root.depth--;
                trace(x, y);
                root.depth++;
            }
        }
    }

    private static void prepare(Simi simi) {
        simi.fixedX = fixed(simi.centerX);
        simi.fixedY = fixed(simi.centerY);
        simi.cosine = fixed((float) Math.cos(simi.angle));
        simi.sine = fixed((float) Math.sin(simi.angle));
        simi.cosine2 = fixed((float) Math.cos(simi.angle2));
        simi.sine2 = fixed((float) Math.sin(simi.angle2));
        simi.fixedRadius = fixed(simi.radius);
        simi.fixedRadius2 = fixed(simi.radius2);
    }

    private static long transform(Simi simi, int originX, int originY) {
        int x = originX - simi.fixedX;
        x = x * simi.fixedRadius >> FIX;
        int y = originY - simi.fixedY;
        y = y * simi.fixedRadius >> FIX;
        int secondX = x - simi.fixedX;
        secondX = secondX * simi.fixedRadius2 >> FIX;
        int secondY = -y - simi.fixedY;
        secondY = secondY * simi.fixedRadius2 >> FIX;
        int resultX = (x * simi.cosine - y * simi.sine + secondX * simi.cosine2 - secondY * simi.sine2 >> FIX)
                + simi.fixedX;
        int resultY = (x * simi.sine + y * simi.cosine + secondX * simi.sine2 + secondY * simi.cosine2 >> FIX)
                + simi.fixedY;
        return (long) resultX << 32 | resultY & 0xffffffffL;
    }

    private static int fixed(float value) {
        return (int) (UNIT * value);
    }
}
