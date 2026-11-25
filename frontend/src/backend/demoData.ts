import type { Hub, Kpis, Stay, Ticket, Trip, Vehicle } from './types';

// Synthetic Vila Ria for the demo adapter. Every name is fictional (Grupo Hoteleiro Marés,
// RodaLeve); the hotel ids match the backend's seed (V1 migration).

export const HOTELS = [
  { id: '00000000-0000-0000-0000-000000000001', name: 'Marés Centro' },
  { id: '00000000-0000-0000-0000-000000000002', name: 'Marés Praia' },
  { id: '00000000-0000-0000-0000-000000000003', name: 'Marés Salinas' },
] as const;

export const DEMO_HUBS: Hub[] = [
  hub('h-centro', 'Marés Centro', 'HOTEL', 'Marés Centro', 40.6413, -8.6537, 12),
  hub('h-praia', 'Marés Praia', 'HOTEL', 'Marés Praia', 40.6434, -8.7466, 10),
  hub('h-salinas', 'Marés Salinas', 'HOTEL', 'Marés Salinas', 40.6331, -8.6655, 8),
  hub('h-historico', 'Centro Histórico', 'TOURIST_SPOT', null, 40.6405, -8.6538, 8),
  hub('h-farol', 'Farol da Barra', 'TOURIST_SPOT', null, 40.6426, -8.7485, 6),
];

function hub(
  id: string,
  name: string,
  kind: Hub['kind'],
  hotel: string | null,
  latitude: number,
  longitude: number,
  capacity: number,
): Hub {
  return {
    id,
    name,
    kind,
    hotel,
    latitude,
    longitude,
    capacity,
    dockedBikes: 0,
    freeSlots: capacity,
  };
}

const FLEET: Array<[string, string | null, Vehicle['status'], number | null]> = [
  ['EB-101', 'h-centro', 'AVAILABLE', 92],
  ['EB-102', 'h-centro', 'AVAILABLE', 81],
  ['EB-103', 'h-centro', 'AVAILABLE', 77],
  ['EB-104', 'h-centro', 'AVAILABLE', 66],
  ['EB-105', 'h-centro', 'AVAILABLE', 58],
  ['EB-106', 'h-centro', 'AVAILABLE', 49],
  ['EB-107', 'h-centro', 'RESERVED', 88],
  ['EB-108', 'h-centro', 'AVAILABLE', 71],
  ['EB-109', 'h-centro', 'AVAILABLE', 64],
  ['EB-110', 'h-centro', 'AVAILABLE', 35],
  ['EB-111', 'h-centro', 'AVAILABLE', 27],
  ['EB-233', 'h-centro', 'MAINTENANCE', 54],
  ['EB-401', 'h-praia', 'AVAILABLE', 86],
  ['EB-388', 'h-praia', 'AVAILABLE', 54],
  ['EB-372', 'h-praia', 'AVAILABLE', 14],
  ['EB-118', 'h-praia', 'MAINTENANCE', 62],
  ['EB-405', 'h-praia', 'AVAILABLE', 73],
  ['EB-409', 'h-praia', 'RESERVED', 91],
  ['EB-412', 'h-praia', 'AVAILABLE', 45],
  ['EB-611', 'h-salinas', 'AVAILABLE', 72],
  ['EB-514', null, 'IN_USE', 61],
  ['EB-602', 'h-historico', 'MAINTENANCE', 44],
  ['EB-520', 'h-historico', 'AVAILABLE', 83],
  ['EB-521', 'h-historico', 'AVAILABLE', 19],
  ['EB-522', 'h-historico', 'AVAILABLE', 68],
  ['EB-523', 'h-historico', 'AVAILABLE', 52],
  ['EB-701', 'h-farol', 'AVAILABLE', 95],
  ['EB-702', 'h-farol', 'AVAILABLE', 40],
  ['EB-099', null, 'RETIRED', null],
];

export function demoVehicles(): Vehicle[] {
  return FLEET.map(([code, hubId, status, batteryPct], index) => ({
    id: `v-${index + 1}`,
    code,
    type: 'E_BIKE',
    status,
    hubId,
    batteryPct,
  }));
}

function hoursFrom(now: number, hours: number): string {
  return new Date(now + hours * 3_600_000).toISOString();
}

export function demoTickets(now: number): Ticket[] {
  return [
    ticket(
      't-1',
      'EB-602',
      'Rear brake does not hold.',
      hoursFrom(now, -20.8),
      hoursFrom(now, 3.2),
    ),
    ticket('t-2', 'EB-118', 'Flat front tyre.', hoursFrom(now, -4.3), hoursFrom(now, 19.7)),
    ticket(
      't-3',
      'EB-233',
      'Rear light does not turn on.',
      hoursFrom(now, -1.9),
      hoursFrom(now, 22.1),
    ),
  ];
}

function ticket(
  id: string,
  vehicleCode: string,
  description: string,
  openedAt: string,
  slaDueAt: string,
): Ticket {
  return {
    id,
    vehicleCode,
    status: 'OPEN',
    description,
    openedAt,
    slaDueAt,
    vehicleStatus: 'MAINTENANCE',
    maintenancePending: false,
  };
}

export function demoTrips(now: number): Trip[] {
  return [
    {
      id: 'trip-past-1',
      status: 'ENDED',
      vehicleCode: 'EB-405',
      requestedAt: hoursFrom(now, -26.2),
      startedAt: hoursFrom(now, -26.2),
      endedAt: hoursFrom(now, -25.4),
      endHubId: 'h-historico',
    },
    {
      id: 'trip-past-2',
      status: 'ENDED',
      vehicleCode: 'EB-520',
      requestedAt: hoursFrom(now, -23),
      startedAt: hoursFrom(now, -23),
      endedAt: hoursFrom(now, -21.6),
      endHubId: 'h-praia',
    },
  ];
}

export function demoStays(now: number): Stay[] {
  const day = (offset: number) => new Date(now + offset * 86_400_000).toISOString().slice(0, 10);
  return [
    stay('s-1', HOTELS[1].id, 'Sophie Laurent', 'PRAIA-7K4Q', day(-1), day(3)),
    stay('s-2', HOTELS[1].id, 'Tomás Ferreira', 'PRAIA-2M9X', day(0), day(2)),
    stay('s-3', HOTELS[0].id, 'Lena Fischer', 'CENTRO-8H3T', day(-2), day(1)),
  ];
}

function stay(
  id: string,
  hotelId: string,
  guestName: string,
  stayCode: string,
  checkIn: string,
  checkOut: string,
): Stay {
  return { id, hotelId, guestName, stayCode, checkIn, checkOut, status: 'ACTIVE' };
}

export const DEMO_KPIS: Kpis = {
  slaTargetPct: 95,
  availability: [
    { hotel: 'Marés Centro', availabilityPct: 97 },
    { hotel: 'Marés Praia', availabilityPct: 96 },
    { hotel: 'Marés Salinas', availabilityPct: 89 },
  ],
  tripsPerDay: [38, 44, 52, 70, 84, 62, 40, 42, 48, 56, 76, 92, 66, 46],
  utilizationPct: 38,
  hoursToRepair: 14,
  passesSold: 212,
  apiP95Ms: 182,
  errorRatePct: 0.3,
  telemetryLagSeconds: 1.4,
};
