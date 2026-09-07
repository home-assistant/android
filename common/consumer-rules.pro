# kotlinx.serialization resolves serializers reflectively at runtime (KType-based lookup, e.g. in
# the WebSocket layer), so R8 cannot trace which serializers get decoded and strips `deserialize`
# from the ones it believes are encode-only, crashing on the first decoded message.
# Keep the generated serializers of all our modules whole.
-keep,includedescriptorclasses class io.homeassistant.companion.**$$serializer { *; }
