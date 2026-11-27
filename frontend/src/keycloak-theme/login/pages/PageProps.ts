import type { PageProps as KcPageProps } from 'keycloakify/login/pages/PageProps';
import type { I18n } from '../i18n';
import type { KcContext } from '../KcContext';

export type PageProps<PageId extends KcContext['pageId']> = KcPageProps<
  Extract<KcContext, { pageId: PageId }>,
  I18n
>;
