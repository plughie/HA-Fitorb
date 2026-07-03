from __future__ import annotations

from datetime import UTC, date, datetime

import pytest

from custom_components.fitorb.relay import (
    RelayAckResult,
    RelayMetric,
    RelayRejectedSample,
    parse_relay_batch,
    relay_ack_to_json,
)


def _payload() -> dict[str, object]:
    return {
        "relay_id": "pixel-8",
        "ring_id": "AA:BB:CC:DD:EE:FF",
        "app_version": "0.1.0",
        "protocol_version": 1,
        "sent_at": "2026-07-03T10:00:00Z",
        "samples": [
            {
                "sample_id": "sample-heart-1",
                "ring_id": "AA:BB:CC:DD:EE:FF",
                "metric": "heart_rate",
                "timestamp": "2026-07-03T09:55:00Z",
                "value": 72,
                "unit": "bpm",
                "source": "android_relay",
                "captured_at": "2026-07-03T09:55:05Z",
                "local_date": "2026-07-03",
                "protocol_version": 1,
            }
        ],
    }


def test_parse_relay_batch_normalizes_timestamps_and_metric() -> None:
    batch = parse_relay_batch(_payload(), max_samples=10)

    assert batch.relay_id == "pixel-8"
    assert batch.ring_id == "AA:BB:CC:DD:EE:FF"
    assert batch.sent_at == datetime(2026, 7, 3, 10, 0, tzinfo=UTC)
    assert len(batch.samples) == 1
    assert batch.samples[0].metric is RelayMetric.HEART_RATE
    assert batch.samples[0].timestamp == datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
    assert batch.samples[0].local_date == date(2026, 7, 3)


def test_parse_relay_batch_rejects_oversized_batches() -> None:
    payload = _payload()
    payload["samples"] = payload["samples"] * 2

    with pytest.raises(ValueError, match="too many samples"):
        parse_relay_batch(payload, max_samples=1)


def test_parse_relay_batch_rejects_metric_mismatch() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["metric"] = "unknown_metric"
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="invalid metric"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_ring_id_mismatch() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["ring_id"] = "11:22:33:44:55:66"
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="ring_id mismatch"):
        parse_relay_batch(payload, max_samples=10)


def test_relay_ack_to_json_uses_zulu_server_time() -> None:
    response = relay_ack_to_json(
        RelayAckResult(
            accepted=("sample-heart-1",),
            duplicates=("sample-heart-0",),
            rejected=(RelayRejectedSample("bad-sample", "invalid_metric"),),
            server_time=datetime(2026, 7, 3, 10, 2, tzinfo=UTC),
        )
    )

    assert response == {
        "accepted": ["sample-heart-1"],
        "duplicates": ["sample-heart-0"],
        "rejected": [{"sample_id": "bad-sample", "reason": "invalid_metric"}],
        "server_time": "2026-07-03T10:02:00Z",
    }
