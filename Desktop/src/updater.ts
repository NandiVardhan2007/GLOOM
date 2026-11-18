export interface UpdateCheckResult {
  available: boolean;
  version?: string;
  date?: string;
  body?: string;
  download_url?: string;
}
