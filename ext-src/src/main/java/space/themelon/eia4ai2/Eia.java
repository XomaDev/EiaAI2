package space.themelon.eia4ai2;

import android.util.Log;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.util.OnInitializeListener;
import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

// Annotations aren't necessary at all since we use a custom build system.
// But for the sake of classic-ness, we still use them
@DesignerComponent(
    category = ComponentCategory.EXTENSION,
    version = 1,
    nonVisible = true
)
@SimpleObject(external = true)
public class Eia extends AndroidNonvisibleComponent implements OnInitializeListener {

  private boolean resetEverytime = true;

  private final File eiaDexFile;

  private Object initInstance;
  private Method executeMethod;
  private Method defineEnvMethod;
  private Method clearMemorySpaceMethod;
  //private Method renderMethod;

  public Eia(Form form) throws IOException {
    super(form);
    eiaDexFile = new File(form.getFilesDir(), "eia-dex.jar");
    copyEiaDex();
    form.registerForOnInitialize(this);
  }

  @Override
  public void onInitialize() {
    // We need to only initialize Eia over here, so that we get to map
    // all the components in the Screen
    try {
      Log.d("Eia", "onInitialize");
      loadEia();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private void loadEia() throws ReflectiveOperationException {
    DexClassLoader dexLoader = new DexClassLoader(
        eiaDexFile.getAbsolutePath(),
        form.getCodeCacheDir().getAbsolutePath(),
        null,
        getClass().getClassLoader()
    );
    Class<?> clazz = dexLoader.loadClass("space.themelon.eia64.AppInventorInterop");
    initInstance = clazz.getField("INSTANCE").get(null);
    Class<?> interopClazz = initInstance.getClass();
    interopClazz.getMethod("init").invoke(initInstance);

    executeMethod = interopClazz.getMethod("execute", String.class);
    defineEnvMethod = interopClazz.getMethod("defineEnv", String.class, Object.class);
    clearMemorySpaceMethod = interopClazz.getMethod("clearMemorySpace");
    //renderMethod = interopClazz.getMethod("render", AndroidViewComponent.class, String.class);
  }

  private void copyEiaDex() throws IOException {
    eiaDexFile.delete();
    InputStream in = form.openAssetForExtension(this, "eia.jar");
    try (FileOutputStream fos = new FileOutputStream(eiaDexFile)) {
      eiaDexFile.setReadOnly();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = in.read(buffer)) > 0) {
        fos.write(buffer, 0, length);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    in.close();
  }

  @DesignerProperty(
      editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "True")
  @SimpleProperty
  public void ResetEverytime(boolean reset) {
    resetEverytime = reset;
  }

  @SimpleProperty
  public boolean ResetEverytime() {
    return resetEverytime;
  }

  @SimpleFunction
  public Object Run(String code) throws Throwable {
    if (resetEverytime) {
      // reset all the environment
      clearMemorySpaceMethod.invoke(initInstance);
    }
    Object[] values = (Object[]) executeMethod.invoke(initInstance, code);
    if ((Boolean) values[0]) {
      Print(new String((byte[]) values[2]));
      return values[1];
    }
    throw (Throwable) values[1];
  }

  @SimpleFunction
  public void Define(String name, Object value) throws ReflectiveOperationException {
    defineEnvMethod.invoke(initInstance, name, value);
  }

  @SimpleEvent
  public void Print(String text) {
    EventDispatcher.dispatchEvent(this, "Print", text);
  }

}