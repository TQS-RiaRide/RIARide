// The hotels of Grupo Hoteleiro Marés, with the ids of the backend's seed (V1 migration). Live
// screens use them until the API can list hotels, so they live apart from the demo data, which
// production builds leave out.
export const HOTELS = [
  { id: '00000000-0000-0000-0000-000000000001', name: 'Marés Centro' },
  { id: '00000000-0000-0000-0000-000000000002', name: 'Marés Praia' },
  { id: '00000000-0000-0000-0000-000000000003', name: 'Marés Salinas' },
] as const;
