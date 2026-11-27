import enAuth from '../locales/en/auth.json';
import enCommand from '../locales/en/command.json';
import enCommon from '../locales/en/common.json';
import enFleet from '../locales/en/fleet.json';
import enGuest from '../locales/en/guest.json';
import enHealth from '../locales/en/health.json';
import enHotel from '../locales/en/hotel.json';
import enLanding from '../locales/en/landing.json';
import enLogin from '../locales/en/login.json';
import enMaintenance from '../locales/en/maintenance.json';
import enShell from '../locales/en/shell.json';
import enUi from '../locales/en/ui.json';
import enWorkspaces from '../locales/en/workspaces.json';
import ptAuth from '../locales/pt/auth.json';
import ptCommand from '../locales/pt/command.json';
import ptCommon from '../locales/pt/common.json';
import ptFleet from '../locales/pt/fleet.json';
import ptGuest from '../locales/pt/guest.json';
import ptHealth from '../locales/pt/health.json';
import ptHotel from '../locales/pt/hotel.json';
import ptLanding from '../locales/pt/landing.json';
import ptLogin from '../locales/pt/login.json';
import ptMaintenance from '../locales/pt/maintenance.json';
import ptShell from '../locales/pt/shell.json';
import ptUi from '../locales/pt/ui.json';
import ptWorkspaces from '../locales/pt/workspaces.json';
import type { Language, Namespace } from './config';

// Translations are bundled with the app: the UI is small, and bundling
// lets i18next initialize synchronously, with no loading state.
export const resources = {
  en: {
    auth: enAuth,
    command: enCommand,
    common: enCommon,
    fleet: enFleet,
    guest: enGuest,
    health: enHealth,
    hotel: enHotel,
    landing: enLanding,
    login: enLogin,
    maintenance: enMaintenance,
    shell: enShell,
    ui: enUi,
    workspaces: enWorkspaces,
  },
  pt: {
    auth: ptAuth,
    command: ptCommand,
    common: ptCommon,
    fleet: ptFleet,
    guest: ptGuest,
    health: ptHealth,
    hotel: ptHotel,
    landing: ptLanding,
    login: ptLogin,
    maintenance: ptMaintenance,
    shell: ptShell,
    ui: ptUi,
    workspaces: ptWorkspaces,
  },
} as const satisfies Record<Language, Record<Namespace, object>>;
