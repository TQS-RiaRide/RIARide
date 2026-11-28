import type {
  Hub,
  HubVehicle,
  Pass,
  PassStatus,
  PassType,
  Stay,
  Ticket,
  Trip,
  TripStatus,
  Vehicle,
  VehicleStatus,
} from './types';
import { PASS_TYPES, VEHICLE_STATUSES } from './types';

// Bodies come from outside the app, so each one is checked before the UI uses it.

export class InvalidResponseError extends Error {
  constructor(what: string) {
    super(`The backend returned an invalid ${what}`);
    this.name = 'InvalidResponseError';
  }
}

type Json = Record<string, unknown>;

function object(value: unknown, what: string): Json {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new InvalidResponseError(what);
  }
  return value as Json;
}

function str(record: Json, key: string, what: string): string {
  const value = record[key];
  if (typeof value !== 'string') throw new InvalidResponseError(what);
  return value;
}

function optStr(record: Json, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' ? value : null;
}

function num(record: Json, key: string, what: string): number {
  const value = record[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new InvalidResponseError(what);
  return value;
}

function optNum(record: Json, key: string): number | null {
  const value = record[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function oneOf<T extends string>(
  record: Json,
  key: string,
  allowed: readonly T[],
  what: string,
): T {
  const value = record[key];
  if (typeof value !== 'string' || !(allowed as readonly string[]).includes(value)) {
    throw new InvalidResponseError(what);
  }
  return value as T;
}

export function list<T>(value: unknown, item: (entry: unknown) => T, what: string): T[] {
  if (!Array.isArray(value)) throw new InvalidResponseError(what);
  return value.map(item);
}

const PASS_STATUSES: readonly PassStatus[] = ['PENDING', 'INACTIVE', 'ACTIVE', 'CLOSED'];
const TRIP_STATUSES: readonly TripStatus[] = ['STARTING', 'IN_USE', 'FAILED', 'ENDED'];

export function parsePass(value: unknown): Pass {
  const r = object(value, 'pass');
  return {
    id: str(r, 'id', 'pass'),
    passType: oneOf<PassType>(r, 'passType', PASS_TYPES, 'pass'),
    status: oneOf(r, 'status', PASS_STATUSES, 'pass'),
    priceCents: num(r, 'priceCents', 'pass'),
    depositCents: num(r, 'depositCents', 'pass'),
    currency: str(r, 'currency', 'pass'),
  };
}

export function parseHub(value: unknown): Hub {
  const r = object(value, 'hub');
  const capacity = num(r, 'capacity', 'hub');
  return {
    id: str(r, 'id', 'hub'),
    name: str(r, 'name', 'hub'),
    kind: oneOf(r, 'kind', ['HOTEL', 'TOURIST_SPOT'] as const, 'hub'),
    hotel: optStr(r, 'hotel'),
    latitude: num(r, 'latitude', 'hub'),
    longitude: num(r, 'longitude', 'hub'),
    capacity,
    dockedBikes: optNum(r, 'dockedBikes') ?? 0,
    freeSlots: optNum(r, 'freeSlots') ?? capacity,
  };
}

export function parseHubVehicle(value: unknown): HubVehicle {
  const r = object(value, 'vehicle');
  return {
    code: str(r, 'code', 'vehicle'),
    type: oneOf(r, 'type', ['E_BIKE'] as const, 'vehicle'),
    status: oneOf<VehicleStatus>(r, 'status', VEHICLE_STATUSES, 'vehicle'),
    batteryPct: optNum(r, 'batteryPct'),
    bookable: r.bookable === true,
  };
}

export function parseVehicle(value: unknown): Vehicle {
  const r = object(value, 'vehicle');
  return {
    id: str(r, 'id', 'vehicle'),
    code: str(r, 'code', 'vehicle'),
    type: oneOf(r, 'type', ['E_BIKE'] as const, 'vehicle'),
    status: oneOf<VehicleStatus>(r, 'status', VEHICLE_STATUSES, 'vehicle'),
    hubId: optStr(r, 'hubId'),
    batteryPct: optNum(r, 'batteryPct'),
  };
}

export function parseTrip(value: unknown): Trip {
  const r = object(value, 'trip');
  return {
    id: str(r, 'id', 'trip'),
    status: oneOf(r, 'status', TRIP_STATUSES, 'trip'),
    vehicleCode: str(r, 'vehicleCode', 'trip'),
    requestedAt: str(r, 'requestedAt', 'trip'),
    startedAt: optStr(r, 'startedAt'),
    endedAt: optStr(r, 'endedAt'),
    endHubId: optStr(r, 'endHubId'),
  };
}

export function parseTicket(value: unknown): Ticket {
  const r = object(value, 'ticket');
  return {
    id: str(r, 'id', 'ticket'),
    vehicleCode: str(r, 'vehicleCode', 'ticket'),
    status: str(r, 'status', 'ticket'),
    description: str(r, 'description', 'ticket'),
    openedAt: str(r, 'openedAt', 'ticket'),
    slaDueAt: str(r, 'slaDueAt', 'ticket'),
    vehicleStatus: oneOf(r, 'vehicleStatus', ['MAINTENANCE', 'IN_USE'] as const, 'ticket'),
    maintenancePending: r.maintenancePending === true,
  };
}

export function parseStay(value: unknown): Stay {
  const r = object(value, 'stay');
  return {
    id: str(r, 'id', 'stay'),
    hotelId: str(r, 'hotelId', 'stay'),
    guestName: str(r, 'guestName', 'stay'),
    stayCode: str(r, 'stayCode', 'stay'),
    checkIn: str(r, 'checkIn', 'stay'),
    checkOut: str(r, 'checkOut', 'stay'),
    status: oneOf(r, 'status', ['ACTIVE', 'CHECKED_OUT'] as const, 'stay'),
  };
}
