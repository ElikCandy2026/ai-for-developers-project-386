import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../contract/tsp-output/openapi/openapi.yaml',
  output: 'src/api/generated',
  plugins: [
    '@hey-api/typescript',
    '@hey-api/sdk',
    {
      name: '@hey-api/client-fetch',
      runtimeConfigPath: './src/data/client-config.ts',
    },
  ],
});
