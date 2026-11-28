// The domain as the web app sees it. Shapes follow the backend's JSON (docs, section 5 of the
// Product Specification), so the live adapter can pass most responses through after checking them.

export type PassType = 'ONE_DAY' | 'THREE_DAY' | 'STAY';
export type PassStatus = 'PENDING' | 'INACTIVE' | 'ACTIVE' | 'CLOSED';
export type VehicleType = 'E_BIKE';
export type VehicleStatus = 'AVAILABLE' | 'RESERVED' | 'IN_USE' | 'MAINTENANCE' | 'RETIRED';
export type HubKind = 'HOTEL' | 'TOURIST_SPOT';
export type TripStatus = 'STARTING' | 'IN_USE' | 'FAILED' | 'ENDED';

export const PASS_TYPES: readonly PassType[] = ['ONE_DAY', 'THREE_DAY', 'STAY'];
export const VEHICLE_STATUSES: readonly VehicleStatus[] = [
  'AVAILABLE',
  'RESERVED',
  'IN_USE',
  'MAINTENANCE',
  'RETIRED',
];

/** A bike below this battery level cannot be booked (BR12). */
export const MIN_BOOKABLE_BATTERY = 20;

/** Statuses an operator may set by hand (RIA-91); the others follow from trips and bookings. */
export const SETTABLE_STATUSES: readonly VehicleStatus[] = ['AVAILABLE', 'MAINTENANCE', 'RETIRED'];

export interface Pass {
  id: string;
  passType: PassType;
  status: PassStatus;
  priceCents: number;
  depositCents: number;
  currency: string;
}

export interface PassPrice {
  passType: PassType;
  priceCents: number;
  depositCents: number;
  currency: string;
}

export interface Hub {
  id: string;
  name: string;
  kind: HubKind;
  hotel: string | null;
  latitude: number;
  longitude: number;
  capacity: number;
  dockedBikes: number;
  freeSlots: number;
}

/** A bike as a hub lists it (GET /api/v1/hubs/{id}/vehicles). */
export interface HubVehicle {
  code: string;
  type: VehicleType;
  status: VehicleStatus;
  batteryPct: number | null;
  bookable: boolean;
}

/** A bike in the operator's fleet view; id is null when this app does not know it yet. */
export interface FleetVehicle extends HubVehicle {
  id: string | null;
  hubId: string | null;
  hubName: string | null;
}

export interface Vehicle {
  id: string;
  code: string;
  type: VehicleType;
  status: VehicleStatus;
  hubId: string | null;
  batteryPct: number | null;
}

export interface Trip {
  id: string;
  status: TripStatus;
  vehicleCode: string;
  requestedAt: string;
  startedAt: string | null;
  endedAt: string | null;
  endHubId: string | null;
}

export interface Ticket {
  id: string;
  vehicleCode: string;
  status: string;
  description: string;
  openedAt: string;
  slaDueAt: string;
  vehicleStatus: 'MAINTENANCE' | 'IN_USE';
  maintenancePending: boolean;
}

export interface Stay {
  id: string;
  hotelId: string;
  guestName: string;
  stayCode: string;
  checkIn: string;
  checkOut: string;
  status: 'ACTIVE' | 'CHECKED_OUT';
}

export interface HotelKpi {
  hotel: string;
  availabilityPct: number;
}

export interface Kpis {
  slaTargetPct: number;
  availability: HotelKpi[];
  tripsPerDay: number[];
  utilizationPct: number;
  hoursToRepair: number;
  passesSold: number;
  apiP95Ms: number;
  errorRatePct: number;
  telemetryLagSeconds: number;
}

export interface RegisterHubRequest {
  name: string;
  kind: HubKind;
  hotel: string | null;
  latitude: number;
  longitude: number;
  capacity: number;
}

export interface RegisterStayRequest {
  hotelId: string;
  guestName: string;
  checkIn: string;
  checkOut: string;
}

export interface SignUpRequest {
  email: string;
  password: string;
  stayCode: string;
  locale: 'pt' | 'en';
}

export interface OpenTicketRequest {
  vehicleCode: string;
  description: string;
}
