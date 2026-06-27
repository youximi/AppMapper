using System.Text.Json;

namespace AppMapper.Controller.Models;

public sealed class ProtocolMessage
{
    public required string Type { get; init; }
    public required JsonElement Root { get; init; }
}
