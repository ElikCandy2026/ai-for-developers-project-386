import type { ClientOptions, Config } from '../api/generated/client';

/**
 * Called by the generated client before the first request. In development and in
 * the packaged application the frontend talks to the same origin, so the base
 * URL is empty by default. VITE_API_BASE_URL can point the SDK at another host.
 */
export function createClientConfig<T extends ClientOptions = ClientOptions>(
  override?: Config<ClientOptions & T>,
): Config<Required<ClientOptions> & T> {
  return {
    ...override,
    baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
  } as Config<Required<ClientOptions> & T>;
}
