package space.themelon.eia4ai2;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
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
// But for sake of classic-ness, we still use them
@DesignerComponent(
    category = ComponentCategory.EXTENSION,
    version = 1,
    nonVisible = true
)
@SimpleObject(external = true)
public class Eia extends AndroidNonvisibleComponent implements OnInitializeListener {

  private final File eiaDexFile;

  private Object initInstance;
  private Method executeMethod;
  private Method renderMethod;

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
    renderMethod = interopClazz.getMethod("render", AndroidViewComponent.class, String.class);
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

  @SimpleFunction
  public Object Run(String code) throws ReflectiveOperationException {
    Object[] values = (Object[]) executeMethod.invoke(initInstance, code);
    Print(new String((byte[]) values[1]));
    return values[0];
  }

  @SimpleFunction
  public void Render(AndroidViewComponent in, String struct) throws ReflectiveOperationException {
    renderMethod.invoke(initInstance, in, struct);
  }

  @SimpleEvent
  public void Print(String text) {
    EventDispatcher.dispatchEvent(this, "Print", text);
  }

}