package snill.client.api.storages.implement;

import snill.client.client.modules.Module;

import java.util.HashMap;
import java.util.Map;

public class LocalizationStorage {

    public enum Language {
        RUSSIAN("Русский"),
        ENGLISH("English"),
        UKRAINIAN("Українська");

        private final String displayName;

        Language(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Language next() {
            Language[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private final Map<String, String> en = new HashMap<>();
    private final Map<String, String> uk = new HashMap<>();
    private final Map<String, String> moduleDescEn = new HashMap<>();
    private final Map<String, String> moduleDescUk = new HashMap<>();
    private Language language = Language.RUSSIAN;

    public LocalizationStorage() {
        add("Режим", "Mode", "Режим");
        add("Мод", "Mode", "Режим");
        add("Стиль", "Style", "Стиль");
        add("Скорость", "Speed", "Швидкість");
        add("Скорость анимации", "Animation Speed", "Швидкість анімації");
        add("Скорость вращения", "Rotation Speed", "Швидкість обертання");
        add("Скорость волн", "Wave Speed", "Швидкість хвиль");
        add("Скорость нитей", "Thread Speed", "Швидкість ниток");
        add("Дистанция", "Distance", "Дистанція");
        add("Размер", "Size", "Розмір");
        add("Прозрачность", "Opacity", "Прозорість");
        add("Свечение", "Glow", "Світіння");
        add("Сила свечения", "Glow Strength", "Сила світіння");
        add("Сила анимации", "Animation Strength", "Сила анімації");
        add("Плавность", "Smoothness", "Плавність");
        add("Анимация", "Animation", "Анімація");
        add("Анимация крыльев", "Wing Animation", "Анімація крил");
        add("Анимация свинга", "Swing Animation", "Анімація свінгу");
        add("Плавная анимация", "Smooth Animation", "Плавна анімація");
        add("Тип частиц", "Particle Type", "Тип частинок");
        add("Количество", "Count", "Кількість");
        add("Приоритет", "Priority", "Пріоритет");
        add("Ротация", "Rotation", "Ротація");
        add("Обход", "Bypass", "Обхід");
        add("Сервер", "Server", "Сервер");
        add("После лута", "After Loot", "Після луту");
        add("Элементы", "Elements", "Елементи");
        add("Ватермарка", "Watermark", "Ватермарка");
        add("Аррай лист", "Array List", "Список модулів");
        add("Горячие клавиши", "Key Binds", "Гарячі клавіші");
        add("Зелья", "Potions", "Зілля");
        add("Таргет худ", "Target HUD", "Таргет HUD");
        add("Уведомления", "Notifications", "Сповіщення");
        add("Стафф", "Staff", "Стаф");
        add("Сессия", "Session", "Сесія");
        add("КейСтроки", "Key Strokes", "Кейстроки");
        add("Информация", "Information", "Інформація");
        add("Обычный", "Default", "Звичайний");
        add("Красивый", "Fancy", "Гарний");
        add("Шейдер", "Shader", "Шейдер");
        add("Нитки", "Threads", "Нитки");
        add("Разлет", "Scatter", "Розліт");
        add("Падение", "Fall", "Падіння");
        add("Возвращаться", "Return", "Повертатися");
        add("Тепаться на спавн", "Teleport to Spawn", "Телепортуватись на спавн");
        add("Картинка 1", "Image 1", "Картинка 1");
        add("Картинка 2", "Image 2", "Картинка 2");
        add("Призраки", "Ghosts", "Привиди");
        add("Райдер", "Rider", "Райдер");
        add("Души", "Souls", "Души");
        add("Кристаллы", "Crystals", "Кристали");
        add("Коллизия", "Collision", "Колізія");
        add("Тест", "Test", "Тест");
        add("Дистанция атаки", "Attack Range", "Дистанція атаки");
        add("Только движение", "Only Movement", "Тільки рух");
        add("Только при Aura", "Only with Aura", "Тільки з Aura");
        add("Только с аурой", "Only with Aura", "Тільки з аурою");
        add("Правая рука X", "Right Hand X", "Права рука X");
        add("Правая рука Y", "Right Hand Y", "Права рука Y");
        add("Правая рука Z", "Right Hand Z", "Права рука Z");
        add("Левая рука X", "Left Hand X", "Ліва рука X");
        add("Левая рука Y", "Left Hand Y", "Ліва рука Y");
        add("Левая рука Z", "Left Hand Z", "Ліва рука Z");
        add("Авто-взлёт", "Auto Takeoff", "Авто зліт");
        add("Обходить Grim", "Bypass Grim", "Обходити Grim");
        add("Крылья", "Wings", "Крила");
        add("Крылья 2", "Wings 2", "Крила 2");
        add("Китайская шляпа", "China Hat", "Китайський капелюх");
        add("Figura", "Figura", "Figura");
        add("AspectRatio", "Aspect Ratio", "Співвідношення сторін");
        add("Соотношение", "Ratio", "Співвідношення");
        add("Влиять на руки", "Affect Hands", "Впливати на руки");
    }

    private void add(String key, String english, String ukrainian) {
        en.put(key, english);
        uk.put(key, ukrainian);
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language == null ? Language.RUSSIAN : language;
    }

    public void cycleLanguage() {
        language = language.next();
    }

    public String translateCategory(Module.ModuleCategory category) {
        return translate(category.getName());
    }

    public String translate(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        return switch (language) {
            case RUSSIAN -> key;
            case ENGLISH -> en.getOrDefault(key, fallbackEnglish(key));
            case UKRAINIAN -> uk.getOrDefault(key, fallbackUkrainian(key));
        };
    }

    public String translateModuleDescription(String description) {
        if (description == null || description.isBlank() || "NULLABLE".equalsIgnoreCase(description) || "desc".equalsIgnoreCase(description)) {
            return "";
        }
        return switch (language) {
            case RUSSIAN -> description;
            case ENGLISH -> moduleDescEn.getOrDefault(description, description);
            case UKRAINIAN -> moduleDescUk.getOrDefault(description, description);
        };
    }

    private String fallbackEnglish(String key) {
        if (key.chars().allMatch(ch -> ch < 128)) {
            return humanizeAscii(key);
        }
        return key;
    }

    private String fallbackUkrainian(String key) {
        if (uk.containsKey(key)) {
            return uk.get(key);
        }
        if (key.chars().allMatch(ch -> ch < 128)) {
            return humanizeAscii(key);
        }
        return key;
    }

    private String humanizeAscii(String key) {
        if (key.indexOf(' ') >= 0) {
            return key;
        }

        String humanized = key.replaceAll("([a-z])([A-Z])", "$1 $2");
        humanized = humanized.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        return humanized.trim();
    }
}
