import { useCallback, useEffect, useState } from 'react';

import type { MeetingType, Owner } from './api/generated';
import { getOwner, listMeetingTypes } from './api/generated';
import { GuestBooking } from './components/GuestBooking';
import { AlertIcon, PhoneIcon, RefreshIcon } from './components/Icons';
import { OwnerDashboard } from './components/OwnerDashboard';
import { apiErrorMessage } from './lib/apiError';
import { formatTimezoneOffset } from './lib/format';

type View = 'guest' | 'owner';

function initialView(): View {
  return window.location.hash === '#owner' ? 'owner' : 'guest';
}

export default function App() {
  const [view, setView] = useState<View>(initialView);
  const [owner, setOwner] = useState<Owner | null>(null);
  const [meetingTypes, setMeetingTypes] = useState<MeetingType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [ownerResult, typesResult] = await Promise.all([getOwner(), listMeetingTypes()]);
      if (ownerResult.error || !ownerResult.data) {
        setError(apiErrorMessage(ownerResult.error, 'Не удалось загрузить данные владельца.'));
        return;
      }
      if (typesResult.error || !typesResult.data) {
        setError(apiErrorMessage(typesResult.error, 'Не удалось загрузить типы встреч.'));
        return;
      }
      setOwner(ownerResult.data);
      setMeetingTypes(typesResult.data);
    } catch {
      setError('Сервер недоступен. Убедитесь, что backend запущен, и попробуйте снова.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const hash = view === 'owner' ? '#owner' : '#guest';
    if (window.location.hash !== hash) {
      window.history.replaceState(null, '', hash);
    }
  }, [view]);

  function handleTypeCreated(meetingType: MeetingType) {
    setMeetingTypes((current) => [...current, meetingType]);
  }

  const initials = owner?.displayName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') ?? '';

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="header-inner">
          <button className="brand" type="button" onClick={() => setView('guest')} aria-label="Callbook, на главную">
            <span className="brand-mark">
              <PhoneIcon />
            </span>
            <span className="brand-text">
              callbook<span>.</span>
            </span>
          </button>

          <nav className="nav" aria-label="Разделы приложения">
            <button
              type="button"
              className={view === 'guest' ? 'active' : ''}
              onClick={() => setView('guest')}
            >
              Записаться
            </button>
            <button
              type="button"
              className={view === 'owner' ? 'active' : ''}
              onClick={() => setView('owner')}
            >
              Кабинет владельца
            </button>
          </nav>

          <div className="header-meta">
            {owner && (
              <span className="header-zone">
                {formatTimezoneOffset(owner.rules.timezone)} · {owner.rules.timezone}
              </span>
            )}
            <span className="header-avatar" aria-hidden="true">{initials}</span>
          </div>
        </div>
      </header>

      {loading ? (
        <main className="center-page">
          <div className="loader-card">
            <span className="brand-mark large">
              <PhoneIcon />
            </span>
            <h1>Загружаем календарь</h1>
            <p>Получаем правила записи и свободные слоты.</p>
            <span className="spinner dark" />
          </div>
        </main>
      ) : error || !owner ? (
        <main className="center-page">
          <div className="loader-card error-card">
            <span className="card-icon error"><AlertIcon /></span>
            <h1>Не удалось открыть календарь</h1>
            <p>{error ?? 'Неизвестная ошибка'}</p>
            <button className="primary-button" type="button" onClick={() => void load()}>
              <RefreshIcon />
              Попробовать снова
            </button>
          </div>
        </main>
      ) : (
        <div className="view-container">
          {view === 'guest' ? (
            <GuestBooking owner={owner} meetingTypes={meetingTypes} onOpenOwner={() => setView('owner')} />
          ) : (
            <OwnerDashboard
              owner={owner}
              meetingTypes={meetingTypes}
              onTypeCreated={handleTypeCreated}
              onOpenGuest={() => setView('guest')}
            />
          )}
        </div>
      )}

      <footer className="site-footer">
        <span>Callbook · учебный сервис записи на звонок</span>
        <span className="footer-tech">TypeSpec → OpenAPI → TypeScript SDK</span>
      </footer>
    </div>
  );
}
