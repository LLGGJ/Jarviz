export type RawRecord = Record<string, unknown>;

export type ResourceStatus = 'online' | 'offline' | 'starting' | 'stopping' | 'error' | 'unknown';

export interface AccountInfo {
  id?: string;
  label: string;
  email?: string;
  plan?: string;
}

/** Os campos opcionais refletem o que a API informar; a documentação v1.4 não define o schema de resposta. */
export interface AppSummary {
  id: string;
  name: string;
  status: ResourceStatus;
  statusRaw?: string;
  runtime?: string;
  ramMb?: number;
  url?: string;
  raw: RawRecord;
}

export interface GameServer {
  id: string;
  name: string;
  game?: string;
  region?: string;
  status: ResourceStatus;
  statusRaw?: string;
  ramMb?: number;
  address?: string;
  raw: RawRecord;
}

export interface DatabaseInstance {
  id: string;
  name: string;
  engine?: string;
  region?: string;
  status: ResourceStatus;
  statusRaw?: string;
  ramMb?: number;
  host?: string;
  raw: RawRecord;
}

export interface StorageObject {
  id: string;
  name: string;
  url?: string;
  size?: number;
  raw: RawRecord;
}

export interface SnapshotInfo {
  id: string;
  name: string;
  resourceId?: string;
  status?: string;
  size?: number;
  createdAt?: string;
  raw: RawRecord;
}

export interface UploadedObject {
  url: string;
  size?: number;
}

export interface FileUpload {
  filename: string;
  data: Uint8Array;
}
