#define WPU_PER_DCM (1200.0 / 2.54)

typedef struct
{
  guchar  fid[4];
  guint32 DataOffset;
  guint8  ProductType;
  guint8  FileType;
  guint8  MajorVersion;
  guint8  MinorVersion;
  guint16 EncryptionKey;
  guint16 Reserved;
}
WPGFileHead;
