import type { ExtendKcContext } from 'keycloakify/login';
import type { KcEnvName, ThemeName } from '../kc.gen';

export interface KcContextExtension {
  themeName: ThemeName;
  properties: Record<KcEnvName, string>;
}

// No page carries extra values: every page reads what Keycloak gives it.
// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export type KcContextExtensionPerPage = {};

export type KcContext = ExtendKcContext<KcContextExtension, KcContextExtensionPerPage>;
