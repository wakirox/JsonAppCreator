import Foundation

/// A standard .zip written in memory, every file stored as it is (the method called "store": photos and most
/// attachments are compressed already).
struct ZipWriter {
    private var body = Data()
    private var directory = Data()
    private var count: UInt16 = 0

    mutating func add(_ path: String, _ bytes: Data) {
        let name = Data(path.utf8)
        let crc = Self.crc32(bytes)
        let (time, date) = Self.dosNow()
        let offset = UInt32(body.count)
        // Local file header: names are UTF-8 (flag bit 11).
        body.append(le: 0x0403_4B50 as UInt32)
        body.append(le: 20 as UInt16)
        body.append(le: 0x0800 as UInt16)
        body.append(le: 0 as UInt16)
        body.append(le: time)
        body.append(le: date)
        body.append(le: crc)
        body.append(le: UInt32(bytes.count))
        body.append(le: UInt32(bytes.count))
        body.append(le: UInt16(name.count))
        body.append(le: 0 as UInt16)
        body.append(name)
        body.append(bytes)
        // The central directory's entry.
        directory.append(le: 0x0201_4B50 as UInt32)
        directory.append(le: 20 as UInt16)
        directory.append(le: 20 as UInt16)
        directory.append(le: 0x0800 as UInt16)
        directory.append(le: 0 as UInt16)
        directory.append(le: time)
        directory.append(le: date)
        directory.append(le: crc)
        directory.append(le: UInt32(bytes.count))
        directory.append(le: UInt32(bytes.count))
        directory.append(le: UInt16(name.count))
        directory.append(le: 0 as UInt16)
        directory.append(le: 0 as UInt16)
        directory.append(le: 0 as UInt16)
        directory.append(le: 0 as UInt16)
        directory.append(le: 0 as UInt32)
        directory.append(le: offset)
        directory.append(name)
        count += 1
    }

    func finished() -> Data {
        var zip = body
        zip.append(directory)
        zip.append(le: 0x0605_4B50 as UInt32)
        zip.append(le: 0 as UInt16)
        zip.append(le: 0 as UInt16)
        zip.append(le: count)
        zip.append(le: count)
        zip.append(le: UInt32(directory.count))
        zip.append(le: UInt32(body.count))
        zip.append(le: 0 as UInt16)
        return zip
    }

    private static let table: [UInt32] = (0..<256).map { index in
        (0..<8).reduce(UInt32(index)) { value, _ in value & 1 == 1 ? 0xEDB8_8320 ^ (value >> 1) : value >> 1 }
    }

    static func crc32(_ data: Data) -> UInt32 {
        ~data.reduce(~UInt32(0)) { crc, byte in table[Int((crc ^ UInt32(byte)) & 0xFF)] ^ (crc >> 8) }
    }

    private static func dosNow() -> (UInt16, UInt16) {
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: Date())
        let year = max((parts.year ?? 1980) - 1980, 0)
        let time = ((parts.hour ?? 0) << 11) | ((parts.minute ?? 0) << 5) | ((parts.second ?? 0) / 2)
        let date = (year << 9) | ((parts.month ?? 1) << 5) | (parts.day ?? 1)
        return (UInt16(time), UInt16(date))
    }
}

private extension Data {
    mutating func append<T: FixedWidthInteger>(le value: T) {
        Swift.withUnsafeBytes(of: value.littleEndian) { append(contentsOf: $0) }
    }
}
