import type {
  FleetVehicle,
  Hub,
  HubVehicle,
  Kpis,
  OpenTicketRequest,
  Pass,
  PassPrice,
  PassType,
  RegisterHubRequest,
  RegisterStayRequest,
  SignUpRequest,
  Stay,
  Ticket,
  Trip,
  Vehicle,
  VehicleStatus,
} from './types';

export type DataMode = 'live' | 'demo';

/**
 * Everything the screens need from the platform: one port, two adapters.
 *
 * - live: the real REST API. Where the API has no read endpoint yet (hub list, my pass, trip
 *   history, ticket list, KPIs: RIA-107, RIA-117, RIA-118, RIA-120, RIA-122), it lists what this
 *   browser created or read, and returns null where nothing can be known.
 * - demo: an in-memory Vila Ria with synthetic data, so the UI runs with no backend.
 *
 * Replacing a stopgap with a real endpoint only changes liveBackend.ts.
 */
export interface Backend {
  readonly mode: DataMode;

  signUpGuest(request: SignUpRequest): Promise<void>;

  passPrices(): Promise<PassPrice[]>;
  currentPass(): Promise<Pass | null>;
  /** Charges the price and the deposit. Retrying with the same key never charges twice. */
  buyPass(passType: PassType, paymentMethod: string, idempotencyKey: string): Promise<Pass>;

  hubs(): Promise<Hub[]>;
  hubVehicles(hubId: string): Promise<HubVehicle[]>;

  startTrip(vehicleCode: string): Promise<Trip>;
  trip(id: string): Promise<Trip>;
  endTrip(id: string, hubId: string): Promise<Trip>;
  myTrips(): Promise<Trip[]>;

  fleet(): Promise<FleetVehicle[]>;
  registerHub(request: RegisterHubRequest): Promise<Hub>;
  registerVehicle(hubId: string, code: string): Promise<Vehicle>;
  changeVehicleStatus(vehicleId: string, status: VehicleStatus): Promise<Vehicle>;

  tickets(): Promise<Ticket[]>;
  openTicket(request: OpenTicketRequest): Promise<Ticket>;

  stays(): Promise<Stay[]>;
  registerStay(request: RegisterStayRequest): Promise<Stay>;

  /** Null when the platform cannot compute them yet. */
  kpis(): Promise<Kpis | null>;
}
