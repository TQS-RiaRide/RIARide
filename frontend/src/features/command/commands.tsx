import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

// Pages add their own actions to the command menu while they are open (for example, "Open a
// ticket" on the tickets page), each with the same shortcut the page answers to.

export interface CommandAction {
  id: string;
  label: string;
  shortcut?: string;
  keywords?: string[];
  run: () => void;
}

interface CommandRegistry {
  actions: CommandAction[];
  setPageActions: (owner: string, actions: CommandAction[]) => void;
  open: boolean;
  setOpen: (open: boolean) => void;
}

const CommandContext = createContext<CommandRegistry | null>(null);

export function CommandProvider({ children }: { children: ReactNode }) {
  const [byOwner, setByOwner] = useState<Record<string, CommandAction[]>>({});
  const [open, setOpen] = useState(false);

  const setPageActions = useCallback((owner: string, actions: CommandAction[]) => {
    setByOwner((current) => {
      if (actions.length > 0) return { ...current, [owner]: actions };
      return Object.fromEntries(Object.entries(current).filter(([key]) => key !== owner));
    });
  }, []);

  const value = useMemo<CommandRegistry>(
    () => ({ actions: Object.values(byOwner).flat(), setPageActions, open, setOpen }),
    [byOwner, setPageActions, open],
  );

  return <CommandContext.Provider value={value}>{children}</CommandContext.Provider>;
}

export function useCommands(): CommandRegistry {
  const value = useContext(CommandContext);
  if (!value) throw new Error('useCommands must be used inside a CommandProvider');
  return value;
}

/** Registers a page's actions for as long as it is mounted; each run uses the latest handler. */
export function usePageCommands(owner: string, actions: CommandAction[]): void {
  const { setPageActions } = useCommands();
  const latest = useRef(actions);
  useEffect(() => {
    latest.current = actions;
  });
  const signature = actions.map((a) => `${a.id}:${a.label}:${a.shortcut ?? ''}`).join('|');

  useEffect(() => {
    const stable = latest.current.map((action) => ({
      ...action,
      run: () => latest.current.find((a) => a.id === action.id)?.run(),
    }));
    setPageActions(owner, stable);
    return () => setPageActions(owner, []);
    // signature stands for the actions' visible content
  }, [owner, signature, setPageActions]);
}
