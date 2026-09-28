package dev.utilityclient.module;

import dev.utilityclient.keybind.KeyBind;

import java.util.List;
import java.util.Locale;

public final class ModuleSetting<T> {
    public enum Type {
        BOOLEAN,
        INTEGER,
        DOUBLE,
        MODE,
        COLOR,
        STRING,
        KEYBIND
    }

    private final String id;
    private final String name;
    private final String description;
    private final Type type;
    private final List<String> options;
    private final double minimum;
    private final double maximum;
    private final double step;
    private final T defaultValue;
    private T value;

    private ModuleSetting(String id, String name, String description, Type type, T value,
                          List<String> options, double minimum, double maximum, double step) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.value = value;
        this.defaultValue = value;
        this.options = options;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public static ModuleSetting<Boolean> booleanSetting(String id, String name, String description, boolean value) {
        return new ModuleSetting<>(id, name, description, Type.BOOLEAN, value, List.of(), 0, 0, 0);
    }

    public static ModuleSetting<Integer> integerSetting(String id, String name, String description,
                                                         int value, int minimum, int maximum, int step) {
        return new ModuleSetting<>(id, name, description, Type.INTEGER, value, List.of(), minimum, maximum, step);
    }

    public static ModuleSetting<Double> doubleSetting(String id, String name, String description,
                                                       double value, double minimum, double maximum, double step) {
        return new ModuleSetting<>(id, name, description, Type.DOUBLE, value, List.of(), minimum, maximum, step);
    }

    public static ModuleSetting<String> modeSetting(String id, String name, String description,
                                                     String value, String... options) {
        return new ModuleSetting<>(id, name, description, Type.MODE, value, List.of(options), 0, 0, 0);
    }

    /**
     * Free text, edited in the settings screen. Used where a fixed list of choices would be
     * wrong, such as a server specific chat command.
     */
    public static ModuleSetting<String> stringSetting(String id, String name, String description, String value) {
        return new ModuleSetting<>(id, name, description, Type.STRING, value, List.of(), 0, 0, 0);
    }

    /**
     * A key bound inside the settings list, separate from the module's own on/off keybind.
     * Used where a module needs an action key that works while the module stays switched on.
     */
    public static ModuleSetting<KeyBind> keybindSetting(String id, String name, String description, KeyBind value) {
        return new ModuleSetting<>(id, name, description, Type.KEYBIND, value, List.of(), 0, 0, 0);
    }

    /**
     * A 24 bit RGB colour (0xRRGGBB) edited with an in-GUI colour picker.
     */
    public static ModuleSetting<Integer> colorSetting(String id, String name, String description, int rgb) {
        return new ModuleSetting<>(id, name, description, Type.COLOR, rgb & 0xFFFFFF, List.of(), 0, 0xFFFFFF, 1);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Type type() {
        return type;
    }

    public T value() {
        return value;
    }

    public String displayValue() {
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "ON" : "OFF";
        }
        if (value instanceof Double doubleValue) {
            return String.format(Locale.ROOT, "%.2f", doubleValue);
        }
        if (value instanceof KeyBind bind) {
            return bind.displayName();
        }
        if (type == Type.COLOR && value instanceof Integer rgb) {
            return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
        }
        return String.valueOf(value);
    }

    /** The key bind behind a KEYBIND setting, or null for every other type. */
    public KeyBind keyBindValue() {
        return value instanceof KeyBind bind ? bind : null;
    }

    public int colorValue() {
        return value instanceof Integer rgb ? rgb & 0xFFFFFF : 0xFFFFFF;
    }

    public void setColor(int rgb) {
        if (type == Type.COLOR) {
            setValue(clampInt(rgb & 0xFFFFFF));
        }
    }

    public void reset() {
        setValue(defaultValue);
    }

    /**
     * Sets a value from code (for example when dragging an element), keeping the
     * configured minimum and maximum.
     */
    @SuppressWarnings("unchecked")
    public void set(T newValue) {
        if (newValue == null) {
            return;
        }
        if (newValue instanceof Integer integerValue) {
            setValue((T) Integer.valueOf(clampInt(integerValue)));
        } else if (newValue instanceof Double doubleValue) {
            setValue((T) Double.valueOf(clampDouble(doubleValue)));
        } else {
            setValue(newValue);
        }
    }

    public void loadValue(Object storedValue) {
        if (storedValue == null) {
            return;
        }
        switch (type) {
            case BOOLEAN -> {
                if (storedValue instanceof Boolean booleanValue) {
                    setValue(booleanValue);
                }
            }
            case INTEGER -> {
                if (storedValue instanceof Number number) {
                    setValue(clampInt(number.intValue()));
                }
            }
            case DOUBLE -> {
                if (storedValue instanceof Number number) {
                    setValue(clampDouble(number.doubleValue()));
                }
            }
            case MODE -> {
                String stringValue = String.valueOf(storedValue);
                if (options.contains(stringValue)) {
                    setValue(stringValue);
                }
            }
            case COLOR -> {
                if (storedValue instanceof Number number) {
                    setValue(clampInt(number.intValue() & 0xFFFFFF));
                } else if (storedValue instanceof String stringValue) {
                    try {
                        setValue(clampInt((int) Long.parseLong(stringValue.replace("#", ""), 16) & 0xFFFFFF));
                    } catch (NumberFormatException ignored) {
                        // keep the current colour when the stored value is not a hex colour
                    }
                }
            }
            case KEYBIND -> {
                if (value instanceof KeyBind bind) {
                    bind.load(String.valueOf(storedValue));
                }
            }
            case STRING -> {
                // Free text is stored as written, with no validation to fail.
                setValue(String.valueOf(storedValue));
            }
        }
    }

    /**
     * Value for the config file. Key binds are flattened to the same short string the
     * module's own keybind uses, so they read the same way in the JSON.
     */
    public Object saveValue() {
        if (value instanceof KeyBind bind) {
            return bind.save();
        }
        return value;
    }

    public void adjust(int direction) {
        switch (type) {
            case BOOLEAN -> setValue(Boolean.valueOf(!((Boolean) value)));
            case INTEGER -> setValue(clampInt((Integer) value + direction * (int) step));
            case DOUBLE -> setValue(clampDouble((Double) value + direction * step));
            case COLOR -> setColor(rotateHue(colorValue(), direction * 30));
            case MODE -> {
                int index = options.indexOf(value);
                if (index >= 0) {
                    setValue(options.get((index + direction + options.size()) % options.size()));
                }
            }
        }
    }

    /**
     * Rotates a colour around the hue wheel, keeping saturation and brightness.
     */
    public static int rotateHue(int rgb, int degrees) {
        float[] hsb = rgbToHsb(rgb);
        float hue = (hsb[0] + degrees / 360.0F) % 1.0F;
        if (hue < 0) {
            hue += 1.0F;
        }
        return hueToRgb(hue, hsb[1], hsb[2]);
    }

    public static int hueToRgb(float hue, float saturation, float brightness) {
        if (saturation <= 0.0F) {
            int grey = Math.round(brightness * 255.0F) & 0xFF;
            return (grey << 16) | (grey << 8) | grey;
        }
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        float f = h - (float) Math.floor(h);
        float p = brightness * (1.0F - saturation);
        float q = brightness * (1.0F - saturation * f);
        float t = brightness * (1.0F - saturation * (1.0F - f));
        float r;
        float g;
        float b;
        switch ((int) h) {
            case 0 -> {
                r = brightness;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = brightness;
                b = p;
            }
            case 2 -> {
                r = p;
                g = brightness;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = brightness;
            }
            case 4 -> {
                r = t;
                g = p;
                b = brightness;
            }
            default -> {
                r = brightness;
                g = p;
                b = q;
            }
        }
        return ((Math.round(r * 255.0F) & 0xFF) << 16)
                | ((Math.round(g * 255.0F) & 0xFF) << 8)
                | (Math.round(b * 255.0F) & 0xFF);
    }

    public static float[] rgbToHsb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float rf = r / 255.0F;
        float gf = g / 255.0F;
        float bf = b / 255.0F;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float hue;
        if (delta <= 0.0F) {
            hue = 0.0F;
        } else if (max == rf) {
            hue = ((gf - bf) / delta) / 6.0F;
        } else if (max == gf) {
            hue = (2.0F + (bf - rf) / delta) / 6.0F;
        } else {
            hue = (4.0F + (rf - gf) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max <= 0.0F ? 0.0F : delta / max;
        return new float[]{hue, saturation, max};
    }

    @SuppressWarnings("unchecked")
    private void setValue(Object nextValue) {
        value = (T) nextValue;
    }

    private int clampInt(int next) {
        return (int) Math.max(minimum, Math.min(maximum, next));
    }

    private double clampDouble(double next) {
        return Math.max(minimum, Math.min(maximum, next));
    }
}
