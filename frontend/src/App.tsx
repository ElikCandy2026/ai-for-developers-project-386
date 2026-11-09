import { useEffect, useMemo, useState } from 'react';

import type { MeetingType } from './data/api';
import { fetchBootstrap } from './data/api';
import { GuestBooking } from './components/GuestBooking';
import { AlertIcon, PhoneIcon, RefreshIcon } from './components/Icons';
import { OwnerDashboard } from './components/OwnerDashboard';
import { useResource } from './data/useResource';
import { formatTimezoneOffset } from './lib/format';

type View = 'guest' | 'owner';

function initialView(): View {
  return window.location.hash === '#owner' ? 'owner' : 'guest';
}

export default function App() {
  const [view, setView] = useState<View>(initialView);
  const { data, loading, error, reload, setData } = useResource(fetchBootstrap, []);
  const owner = data?.owner ?? null;
  const meetingTypes = data?.meetingTypes ?? [];

  useEffect(() => {
    const hash = view === 'owner' ? '#owner' : '#guest';
    if (window.location.hash !== hash) {
      window.history.replaceState(null, '', hash);
    }
  }, [view]);

  function handleTypeCreated(meetingType: MeetingType) {
    setData((current) =>
      current ? { ...current, meetingTypes: [...current.meetingTypes, meetingType] } : current,
    );
  }

  const initials = useMemo(
    () =>
      owner?.displayName
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase())
        .join('') ?? '',
    [owner],
  );

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
            <p>{error?.message ?? 'Неизвестная ошибка'}</p>
            <button className="primary-button" type="button" onClick={reload}>
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
