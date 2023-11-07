package com.hato.trustmealready;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

import com.hato.trustmealready.CustomClass.TrustManager;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.KeyManager;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;

public class Main implements IXposedHookZygoteInit {

    private static final String SSL_CLASS_NAME = "com.android.org.conscrypt.TrustManagerImpl";
    private static final String SSL_METHOD_NAME = "checkTrustedRecursive";
    private static final Class<?> SSL_RETURN_TYPE = List.class;
    private static final Class<?> SSL_RETURN_PARAM_TYPE = X509Certificate.class;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        XposedBridge.log("TrustMeAlready loading...");
        int hookedMethods = 0;

        for (Method method : findClass(SSL_CLASS_NAME, null).getDeclaredMethods()) {
            if (!checkSSLMethod(method)) {
                continue;
            }

            List<Object> params = new ArrayList<>();
            params.addAll(Arrays.asList(method.getParameterTypes()));
            params.add(new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return new ArrayList<X509Certificate>();
                }
            });

            XposedBridge.log("Hooking method:");
            XposedBridge.log(method.toString());
            findAndHookMethod(SSL_CLASS_NAME, null, SSL_METHOD_NAME, params.toArray());
            hookedMethods++;
        }

        for (Method method : findClass("com.android.org.conscrypt.ConscryptFileDescriptorSocket", null).getDeclaredMethods()) {
            if (method.getName().equals("verifyCertificateChain")) {
                List<Object> params = new ArrayList<>();
                params.addAll(Arrays.asList(method.getParameterTypes()));
                params.add(new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return null;
                    }
                });
                XposedBridge.log("Hooking method:");
                XposedBridge.log(method.toString());
                findAndHookMethod("com.android.org.conscrypt.ConscryptFileDescriptorSocket", null, "verifyCertificateChain", params.toArray());
                hookedMethods++;
            }
        }

        for (Method method : findClass("javax.net.ssl.HttpsURLConnection", null).getDeclaredMethods()) {
            String name = method.getName();
            if (name.equals("setHostnameVerifier") || name.equals("setSSLSocketFactory") || name.equals("setDefaultHostnameVerifier")) {
                List<Object> params = new ArrayList<>();
                params.addAll(Arrays.asList(method.getParameterTypes()));
                params.add(new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return null;
                    }
                });
                XposedBridge.log("Hooking method:");
                XposedBridge.log(method.toString());
                findAndHookMethod("javax.net.ssl.HttpsURLConnection", null,
                        method.getName(), params.toArray());
                hookedMethods++;
            }
        }

        for (Method method : findClass("javax.net.ssl.SSLContext", null).getDeclaredMethods()) {
            if ("init".equals(method.getName())) {
                List<Object> params = new ArrayList<>();
                params.addAll(Arrays.asList(method.getParameterTypes()));
                params.add(new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        // Obtiene los argumentos del método original
                        KeyManager[] keyManagers = (KeyManager[]) param.args[0];
                        javax.net.ssl.TrustManager[] originalTrustManagers = (javax.net.ssl.TrustManager[]) param.args[1];
                        SecureRandom secureRandom = (SecureRandom) param.args[2];

                        // Crea un nuevo TrustManager personalizado
                        TrustManager[] customTrustManagers = new com.hato.trustmealready.CustomClass.TrustManager[]
                                { new com.hato.trustmealready.CustomClass.TrustManager() };

                        // Reemplaza los TrustManagers originales con el TrustManager personalizado
                        param.args[1] = customTrustManagers;

                        // Llama al método original con los argumentos modificados
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }
                });
                XposedBridge.log("Hooking method:");
                XposedBridge.log(method.toString());
                findAndHookMethod("javax.net.ssl.SSLContext", null, "init", params.toArray());
                hookedMethods++;
            }
        }

        XposedBridge.log(String.format(Locale.ENGLISH, "TrustMeAlready loaded! Hooked %d methods", hookedMethods));
    }

    private boolean checkSSLMethod(Method method) {
        if (!method.getName().equals(SSL_METHOD_NAME)) {
            return false;
        }

        // check return type
        if (!SSL_RETURN_TYPE.isAssignableFrom(method.getReturnType())) {
            return false;
        }

        // check if parameterized return type
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) {
            return false;
        }

        // check parameter type
        Type[] args = ((ParameterizedType) returnType).getActualTypeArguments();
        return args.length == 1 && args[0].equals(SSL_RETURN_PARAM_TYPE);
    }
}