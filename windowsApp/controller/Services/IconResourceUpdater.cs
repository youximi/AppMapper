using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;

namespace AppMapper.Controller.Services;

public static class IconResourceUpdater
{
    private const uint RtIcon = 3;
    private const uint RtGroupIcon = 14;
    public static byte[] CreateIcoFromPng(byte[] pngBytes)
    {
        using var imageStream = new MemoryStream(pngBytes);
        using var image = Image.FromStream(imageStream);
        var width = image.Width >= 256 ? 0 : image.Width;
        var height = image.Height >= 256 ? 0 : image.Height;

        using var output = new MemoryStream();
        using var writer = new BinaryWriter(output);
        writer.Write((ushort)0);
        writer.Write((ushort)1);
        writer.Write((ushort)1);
        writer.Write((byte)width);
        writer.Write((byte)height);
        writer.Write((byte)0);
        writer.Write((byte)0);
        writer.Write((ushort)1);
        writer.Write((ushort)32);
        writer.Write((uint)pngBytes.Length);
        writer.Write((uint)22);
        writer.Write(pngBytes);
        return output.ToArray();
    }

    public static void WriteIconResource(string exePath, byte[] icoBytes)
    {
        var icon = ParseSingleIcon(icoBytes);
        var handle = BeginUpdateResource(exePath, false);
        if (handle == IntPtr.Zero) throw new InvalidOperationException("BeginUpdateResource failed.");

        try
        {
            if (!UpdateResource(handle, MakeIntResource(RtIcon), MakeIntResource(1), 0, icon.ImageBytes, icon.ImageBytes.Length))
                throw new InvalidOperationException("Update RT_ICON failed.");

            var group = CreateGroupIcon(icon, 1);
            if (!UpdateResource(handle, MakeIntResource(RtGroupIcon), MakeIntResource(1), 0, group, group.Length))
                throw new InvalidOperationException("Update RT_GROUP_ICON failed.");

            if (!EndUpdateResource(handle, false))
                throw new InvalidOperationException("EndUpdateResource failed.");
        }
        catch
        {
            EndUpdateResource(handle, true);
            throw;
        }
    }

    private static IconEntry ParseSingleIcon(byte[] icoBytes)
    {
        using var stream = new MemoryStream(icoBytes);
        using var reader = new BinaryReader(stream);
        reader.ReadUInt16();
        reader.ReadUInt16();
        var count = reader.ReadUInt16();
        if (count == 0) throw new InvalidOperationException("ICO has no images.");

        var width = reader.ReadByte();
        var height = reader.ReadByte();
        var colorCount = reader.ReadByte();
        var reserved = reader.ReadByte();
        var planes = reader.ReadUInt16();
        var bitCount = reader.ReadUInt16();
        var bytesInRes = reader.ReadUInt32();
        var imageOffset = reader.ReadUInt32();

        stream.Position = imageOffset;
        var imageBytes = reader.ReadBytes((int)bytesInRes);
        return new IconEntry(width, height, colorCount, reserved, planes, bitCount, bytesInRes, imageBytes);
    }

    private static byte[] CreateGroupIcon(IconEntry icon, ushort resourceId)
    {
        using var output = new MemoryStream();
        using var writer = new BinaryWriter(output);
        writer.Write((ushort)0);
        writer.Write((ushort)1);
        writer.Write((ushort)1);
        writer.Write(icon.Width);
        writer.Write(icon.Height);
        writer.Write(icon.ColorCount);
        writer.Write(icon.Reserved);
        writer.Write(icon.Planes);
        writer.Write(icon.BitCount);
        writer.Write(icon.BytesInRes);
        writer.Write(resourceId);
        return output.ToArray();
    }

    private static IntPtr MakeIntResource(uint value) => new((int)value);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr BeginUpdateResource(string pFileName, bool bDeleteExistingResources);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool UpdateResource(
        IntPtr hUpdate,
        IntPtr lpType,
        IntPtr lpName,
        ushort wLanguage,
        byte[] lpData,
        int cbData);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EndUpdateResource(IntPtr hUpdate, bool fDiscard);

    private sealed record IconEntry(
        byte Width,
        byte Height,
        byte ColorCount,
        byte Reserved,
        ushort Planes,
        ushort BitCount,
        uint BytesInRes,
        byte[] ImageBytes);
}
