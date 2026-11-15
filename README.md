# NE.HOOD · App Android (tienda y CRM)

Aplicativo Android nativo de la tienda NE.HOOD con tres roles (comprador, vendedor y administrador),
base de datos SQLite local y pagos por ePayco. No necesita configurar ningún servicio externo para compilar.

| Componente | Tecnología |
|---|---|
| Lenguaje e interfaz | Kotlin, layouts XML, View Binding, Material Components 3 |
| Base de datos | SQLite (`SQLiteOpenHelper`), archivo `nehood.db` |
| Autenticación | Usuarios en SQLite con contraseña en hash PBKDF2 + sal; huella con BiometricPrompt y Android Keystore |
| Imágenes | Cámara o galería; fotos comprimidas a JPEG de máximo 1280 px en el almacenamiento interno |
| Ubicación | Fused Location Provider de Google Play Services + Geocoder |
| Pagos | ePayco Checkout (modo pruebas) con validación de la transacción |
| Android | mínimo 8.0 (API 26), objetivo API 34 |

## Cuentas iniciales

La base de datos se crea la primera vez que se abre la app, con estas cuentas y 4 productos de ejemplo:

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | admin@nehood.co | nehood123 |
| Vendedor | vendedor@nehood.co | nehood123 |

Los compradores se crean desde **Crear cuenta** en la app.

## Arquitectura

```
ui (Activities / Fragments, layouts XML)
   │  se suscriben a cambios y llaman a los repositorios
   ▼
data (repositorios) ── AuthRepository, UserRepository, ProductRepository,
   │                    CartRepository, OrderRepository, SalesStats
   ▼                    ChangeBus avisa a las pantallas cuando cambia una tabla
SQLite (Database) · almacenamiento interno (fotos) · ePayco · Google Play Services (ubicación)
```

Paquetes: `auth` (splash, login, registro, recuperar contraseña), `buyer`, `seller`, `admin`,
`common` (perfil), `payment` (ePayco), `core` (sesión, huella, utilidades), `data` (base de datos y repositorios).

### Tablas

| Tabla | Columnas principales |
|---|---|
| `users` | id, name, email (única), phone, password_hash, salt, role, level, active, purchases, last_change |
| `products` | id, name, description, collection, category, price, stock, sizes, color_hex, image_path, published, units_sold |
| `cart_items` | id, user_id → users, product_id → products, size, qty (único por usuario + producto + talla) |
| `orders` | id, invoice (factura ePayco), user_id, customer, subtotal, shipping, total, status, payment_status, payment_ref, pickup, address, lat, lng, created_at |
| `order_items` | id, order_id → orders, product_id, name, size, qty, price |

## Puesta en marcha

1. Abrir el proyecto en Android Studio. Si aparece *Incompatible Gradle JVM*, en
   Settings → Build Tools → Gradle elegir **Gradle JDK 17 o 21**.
2. **ePayco:** crear una cuenta en <https://epayco.com>, entrar en modo pruebas y copiar la **llave pública**
   (Integraciones → Llaves API) en `gradle.properties`:
   ```
   EPAYCO_PUBLIC_KEY=tu_llave_publica
   EPAYCO_TEST=true
   ```
   Para pagar en modo pruebas, usar las tarjetas de prueba de la documentación de ePayco (docs.epayco.com).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
4. La huella requiere un **dispositivo físico** con al menos una huella registrada.

## Funcionalidades por requisito

| Requisito | Dónde está |
|---|---|
| Registro, login, cierre de sesión, recuperación | `auth/*`, `data/AuthRepository.kt`, `data/PasswordHasher.kt` |
| Huella en dispositivo físico | `auth/LoginActivity.kt`, `core/BiometricStore.kt` |
| CRUD de usuarios (administrador) | `admin/AdminUsersFragment.kt`, `admin/UserFormActivity.kt`, `data/UserRepository.kt` |
| CRUD de productos con cámara o galería | `seller/ProductFormActivity.kt`, `core/ImageCompressor.kt`, `data/ProductRepository.kt` |
| Carrito y órdenes con historial | `buyer/CartFragment.kt`, `data/CartRepository.kt`, `data/OrderRepository.kt`, `seller/SellerOrdersFragment.kt` |
| Geolocalización de la dirección de entrega | `buyer/CheckoutActivity.kt` (`fetchLocation`) |
| Pasarela de pagos con confirmación | `payment/PaymentActivity.kt`, `payment/EpaycoClient.kt` |

## Limitaciones conocidas
- Los datos viven en el celular: cada instalación tiene su propia base de datos. Para compartir datos
  entre celulares se reemplazarían los repositorios por una base en la nube sin cambiar las pantallas.
- La recuperación de contraseña verifica correo + celular registrados, porque no hay servidor de correo.
- El pago se confirma desde la app consultando el servicio de validación de ePayco y comparando
  factura y valor. En producción esa confirmación debería hacerla un servidor (URL de confirmación).
- El inicio de sesión con Google y el pago por cuotas del Casillero quedan para una próxima versión.
