package com.luban;

import sun.security.action.GetPropertyAction;
import sun.util.locale.BaseLocale;
import sun.util.locale.LocaleExtensions;
import sun.util.locale.LocaleObjectCache;
import sun.util.locale.LocaleUtils;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.util.Locale;

public class TestLanguage {

    public static void main(String[] args) {
        Locale locale = initDefault();
        System.out.println(locale);
    }

    private static Locale initDefault() {
        String language, region, script, country, variant;
        language = AccessController.doPrivileged(
                new GetPropertyAction("user.language", "en"));
        // for compatibility, check for old user.region property
        region = AccessController.doPrivileged(
                new GetPropertyAction("user.region"));
        if (region != null) {
            // region can be of form country, country_variant, or _variant
            int i = region.indexOf('_');
            if (i >= 0) {
                country = region.substring(0, i);
                variant = region.substring(i + 1);
            } else {
                country = region;
                variant = "";
            }
            script = "";
        } else {
            script = AccessController.doPrivileged(
                    new GetPropertyAction("user.script", ""));
            country = AccessController.doPrivileged(
                    new GetPropertyAction("user.country", ""));
            variant = AccessController.doPrivileged(
                    new GetPropertyAction("user.variant", ""));
        }

        return getInstance(language, script, country, variant, null);
    }


    static Locale getInstance(String language, String script, String country,
                              String variant, LocaleExtensions extensions) {
        if (language == null || script == null || country == null || variant == null) {
            throw new NullPointerException();
        }

        if (extensions == null) {
            extensions = getCompatibilityExtensions(language, script, country, variant);
        }

        BaseLocale baseloc = BaseLocale.getInstance(language, script, country, variant);
        return getInstance(baseloc, extensions);
    }


    private static LocaleExtensions getCompatibilityExtensions(String language,
                                                               String script,
                                                               String country,
                                                               String variant) {
        LocaleExtensions extensions = null;
        // Special cases for backward compatibility support
        if (LocaleUtils.caseIgnoreMatch(language, "ja")
                && script.length() == 0
                && LocaleUtils.caseIgnoreMatch(country, "jp")
                && "JP".equals(variant)) {
            // ja_JP_JP -> u-ca-japanese (calendar = japanese)
            extensions = LocaleExtensions.CALENDAR_JAPANESE;
        } else if (LocaleUtils.caseIgnoreMatch(language, "th")
                && script.length() == 0
                && LocaleUtils.caseIgnoreMatch(country, "th")
                && "TH".equals(variant)) {
            // th_TH_TH -> u-nu-thai (numbersystem = thai)
            extensions = LocaleExtensions.NUMBER_THAI;
        }
        return extensions;
    }


    static Locale getInstance(BaseLocale baseloc, LocaleExtensions extensions) {
        LocaleKey key = new LocaleKey(baseloc, extensions);
        return LOCALECACHE.get(key);
    }

    static private final Cache LOCALECACHE = new Cache();

    //   private Locale(BaseLocale baseLocale, LocaleExtensions extensions) {
    private static class Cache extends LocaleObjectCache<LocaleKey, Locale> {
        private Cache() {

        }

        @Override
        protected Locale createObject(LocaleKey key) {
            try {
                //return new Locale(key.base, key.exts); 因为是私有方法，只能通过反射来调用构造方法
                Constructor methods = Locale.class.getDeclaredConstructor(BaseLocale.class, LocaleExtensions.class);
                System.out.println("反射创建 Local");
                methods.setAccessible(true);
                Locale locale = (Locale) methods.newInstance(key.base, key.exts);
                return locale;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    private static final class LocaleKey {
        private final BaseLocale base;
        private final LocaleExtensions exts;
        private final int hash;

        private LocaleKey(BaseLocale baseLocale, LocaleExtensions extensions) {
            base = baseLocale;
            exts = extensions;

            // Calculate the hash value here because it's always used.
            int h = base.hashCode();
            if (exts != null) {
                h ^= exts.hashCode();
            }
            hash = h;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LocaleKey)) {
                return false;
            }
            LocaleKey other = (LocaleKey) obj;
            if (hash != other.hash || !base.equals(other.base)) {
                return false;
            }
            if (exts == null) {
                return other.exts == null;
            }
            return exts.equals(other.exts);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

}
